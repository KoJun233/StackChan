import { describe, expect, it, vi } from 'vitest'
import { StreamMessageServerError, streamMessage } from './companion'

function sseResponse(body: string): Response {
  const bytes = new TextEncoder().encode(body)
  return new Response(new ReadableStream({
    start(controller) {
      controller.enqueue(bytes)
      controller.close()
    },
  }), {
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

describe('streamMessage', () => {
  it('parses POST SSE deltas and completion in order', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([
      'event: delta',
      'data: {"messageId":"a","text":"你好"}',
      '',
      'event: completed',
      'data: {"messageId":"a","content":"你好！"}',
      '',
    ].join('\n'))))
    const onDelta = vi.fn()
    const onCompleted = vi.fn()

    await streamMessage('conversation-id', {
      clientMessageId: 'client-message-id',
      content: '你好',
    }, { onDelta, onCompleted })

    expect(onDelta).toHaveBeenCalledWith({ messageId: 'a', text: '你好' })
    expect(onCompleted).toHaveBeenCalledWith({ messageId: 'a', content: '你好！' })
  })

  it('turns an SSE error event into a Chinese error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([
      'event: error',
      'data: {"messageId":"a","code":"provider_unavailable","message":"模型服务暂时不可用"}',
      '',
    ].join('\n'))))

    await expect(streamMessage('conversation-id', {
      clientMessageId: 'client-message-id',
      content: '你好',
    }, {})).rejects.toThrow('模型服务暂时不可用')
  })
  it('exposes the safe server error code', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([
      'event: error',
      'data: {"code":"provider_unavailable","message":"Service unavailable"}',
      '',
    ].join('\n'))))
    const onError = vi.fn()

    let error: unknown
    try {
      await streamMessage('conversation-id', {
        clientMessageId: 'client-message-id',
        content: 'hello',
      }, { onError })
    }
    catch (caught) {
      error = caught
    }

    expect(error).toBeInstanceOf(StreamMessageServerError)
    expect((error as StreamMessageServerError).code).toBe('provider_unavailable')
    expect(onError).toHaveBeenCalledWith(error)
  })

  it('parses an interrupted terminal event', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(sseResponse([
      'event: interrupted',
      'data: {"messageId":"a","content":"partial"}',
      '',
    ].join('\n'))))
    const onInterrupted = vi.fn()

    await streamMessage('conversation-id', {
      clientMessageId: 'client-message-id',
      content: 'hello',
    }, { onInterrupted })

    expect(onInterrupted).toHaveBeenCalledWith({ messageId: 'a', content: 'partial' })
  })
})
