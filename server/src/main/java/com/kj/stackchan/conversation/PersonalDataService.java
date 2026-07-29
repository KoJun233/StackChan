package com.kj.stackchan.conversation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalDataService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_CONVERSATIONS = 1_000;
    private static final int MAX_QUERY_LENGTH = 200;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    public PersonalDataService(
            ConversationRepository conversationRepository,
            ConversationMessageRepository messageRepository,
            NamedParameterJdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ConversationPage list(ConversationFilter filter, int from, int limit) {
        validateFilter(filter);
        if (from < 0 || limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new InvalidPersonalDataRequestException("Pagination is outside the allowed range");
        }
        QueryParts query = queryParts(filter);
        long total = jdbcTemplate.queryForObject(
                "select count(*) from conversations c " + query.joins() + query.where(),
                query.parameters(),
                Long.class
        );
        MapSqlParameterSource pageParameters = new MapSqlParameterSource(query.parameters().getValues())
                .addValue("limit", limit)
                .addValue("offset", from);
        List<ConversationSummary> conversations = jdbcTemplate.query("""
                select c.id, c.title, c.created_at, c.updated_at,
                       dvc.device_id, d.display_name,
                       (select count(*) from conversation_messages cm where cm.conversation_id = c.id) as message_count
                  from conversations c
                """ + query.joins() + query.where() + """
                 order by c.updated_at desc, c.id desc
                 limit :limit offset :offset
                """, pageParameters, this::mapConversation);
        return new ConversationPage(conversations, total);
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageSnapshot> messages(UUID conversationId) {
        requireConversation(conversationId);
        return messageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId).stream()
                .map(message -> new ConversationMessageSnapshot(
                        message.getId(), message.getRole(), message.getContent(), message.getGenerationStatus(),
                        message.getCreatedAt(), message.getCompletedAt()
                ))
                .toList();
    }

    @Transactional
    public void deleteMessage(UUID conversationId, UUID messageId) {
        ConversationEntity conversation = requireConversation(conversationId);
        ConversationMessageEntity message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new PersonalDataNotFoundException("Conversation message was not found"));
        if (message.getGenerationStatus() == GenerationStatus.STREAMING) {
            throw new PersonalDataConflictException("A streaming message cannot be deleted");
        }
        messageRepository.delete(message);
        messageRepository.flush();
        Instant latestMessageTime = messageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId)
                .stream()
                .map(ConversationMessageEntity::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(conversation.getCreatedAt());
        conversation.touch(latestMessageTime);
    }

    @Transactional
    public void deleteConversation(UUID conversationId) {
        requireConversation(conversationId);
        if (messageRepository.countByConversationIdAndGenerationStatus(
                conversationId, GenerationStatus.STREAMING
        ) > 0) {
            throw new PersonalDataConflictException("A conversation with a streaming message cannot be deleted");
        }
        conversationRepository.deleteById(conversationId);
    }

    @Transactional(readOnly = true)
    public ConversationExport export(ConversationFilter filter, UUID conversationId) {
        validateFilter(filter);
        ConversationFilter effectiveFilter = filter.withConversationId(conversationId);
        ConversationPage page = list(effectiveFilter, 0, MAX_PAGE_SIZE);
        if (page.total() > MAX_EXPORT_CONVERSATIONS) {
            throw new InvalidPersonalDataRequestException("Export contains too many conversations; narrow the filter");
        }
        List<ConversationSummary> summaries = new ArrayList<>(page.list());
        int offset = summaries.size();
        while (offset < page.total()) {
            ConversationPage next = list(effectiveFilter, offset, MAX_PAGE_SIZE);
            summaries.addAll(next.list());
            offset += next.list().size();
        }
        List<ExportedConversation> conversations = summaries.stream()
                .map(summary -> new ExportedConversation(summary, messages(summary.id())))
                .toList();
        return new ConversationExport(
                1,
                clock.instant(),
                new ExportFilter(
                        effectiveFilter.query(), effectiveFilter.deviceId(), effectiveFilter.fromTime(),
                        effectiveFilter.toTime(), effectiveFilter.conversationId()
                ),
                conversations
        );
    }

    private ConversationEntity requireConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new PersonalDataNotFoundException("Conversation was not found"));
    }

    private void validateFilter(ConversationFilter filter) {
        if (filter.query() != null && filter.query().length() > MAX_QUERY_LENGTH) {
            throw new InvalidPersonalDataRequestException("Search query is too long");
        }
        if (filter.fromTime() != null && filter.toTime() != null && filter.fromTime().isAfter(filter.toTime())) {
            throw new InvalidPersonalDataRequestException("The start time must not be after the end time");
        }
    }

    private QueryParts queryParts(ConversationFilter filter) {
        StringBuilder joins = new StringBuilder(" left join device_voice_conversations dvc on dvc.conversation_id = c.id")
                .append(" left join devices d on d.id = dvc.device_id ");
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filter.conversationId() != null) {
            conditions.add("c.id = :conversationId");
            parameters.addValue("conversationId", filter.conversationId());
        }
        if (filter.deviceId() != null) {
            conditions.add("dvc.device_id = :deviceId");
            parameters.addValue("deviceId", filter.deviceId());
        }
        if (filter.fromTime() != null) {
            conditions.add("c.updated_at >= :fromTime");
            parameters.addValue("fromTime", Timestamp.from(filter.fromTime()));
        }
        if (filter.toTime() != null) {
            conditions.add("c.updated_at <= :toTime");
            parameters.addValue("toTime", Timestamp.from(filter.toTime()));
        }
        String normalizedQuery = filter.query() == null ? "" : filter.query().strip().toLowerCase();
        if (!normalizedQuery.isEmpty()) {
            conditions.add("(lower(c.title) like :query escape '!' or exists (" +
                    "select 1 from conversation_messages search_message " +
                    "where search_message.conversation_id = c.id " +
                    "and lower(search_message.content) like :query escape '!'))");
            parameters.addValue("query", "%" + escapeLike(normalizedQuery) + "%");
        }
        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        return new QueryParts(joins.toString(), where, parameters);
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private ConversationSummary mapConversation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ConversationSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("title"),
                resultSet.getObject("device_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getLong("message_count"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    public record ConversationFilter(
            String query,
            UUID deviceId,
            Instant fromTime,
            Instant toTime,
            UUID conversationId
    ) {
        public ConversationFilter withConversationId(UUID id) {
            return new ConversationFilter(query, deviceId, fromTime, toTime, id == null ? conversationId : id);
        }
    }

    public record ConversationSummary(
            UUID id,
            String title,
            UUID deviceId,
            String deviceName,
            long messageCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ConversationPage(List<ConversationSummary> list, long total) {
    }

    public record ExportFilter(String query, UUID deviceId, Instant fromTime, Instant toTime, UUID conversationId) {
    }

    public record ExportedConversation(ConversationSummary conversation, List<ConversationMessageSnapshot> messages) {
    }

    public record ConversationExport(
            int schemaVersion,
            Instant exportedAt,
            ExportFilter filter,
            List<ExportedConversation> conversations
    ) {
    }

    private record QueryParts(String joins, String where, MapSqlParameterSource parameters) {
    }
}
