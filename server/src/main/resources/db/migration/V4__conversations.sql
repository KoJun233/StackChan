CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE conversation_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    client_message_id UUID,
    in_reply_to_message_id UUID REFERENCES conversation_messages(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content TEXT NOT NULL DEFAULT '',
    generation_status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
        CHECK (generation_status IN ('STREAMING', 'COMPLETED', 'INTERRUPTED', 'FAILED')),
    failure_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX conversation_messages_client_message_uq
    ON conversation_messages(conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE UNIQUE INDEX conversation_messages_reply_to_uq
    ON conversation_messages(in_reply_to_message_id)
    WHERE in_reply_to_message_id IS NOT NULL;

CREATE INDEX conversation_messages_order_idx
    ON conversation_messages(conversation_id, created_at, id);
