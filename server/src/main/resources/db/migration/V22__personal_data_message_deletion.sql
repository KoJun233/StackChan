ALTER TABLE conversation_messages
    DROP CONSTRAINT conversation_messages_in_reply_to_message_id_fkey;

ALTER TABLE conversation_messages
    ADD CONSTRAINT conversation_messages_in_reply_to_message_id_fkey
    FOREIGN KEY (in_reply_to_message_id)
    REFERENCES conversation_messages(id)
    ON DELETE SET NULL;

CREATE INDEX conversations_updated_at_idx
    ON conversations(updated_at DESC, id DESC);

CREATE INDEX device_voice_conversations_conversation_idx
    ON device_voice_conversations(conversation_id, device_id);
