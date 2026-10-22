import { get, post } from './http'

export interface Conversation { id: number; title: string; createdAt: string; updatedAt: string }
export interface ChatTurn { id: number; conversationId: number; userText: string; replyText?: string; evidenceJson?: string; reportId?: number; status: 'PENDING' | 'COMPLETE' | 'FAILED'; errorMessage?: string; requestKey: string; leaseUntil: string; createdAt: string }
export interface ChatEvidence { kind?: 'MODEL' | 'READ' | 'CACHE' | 'RESULT'; state?: 'RUNNING' | 'DONE' | 'FAILED'; tool?: string; label: string; observedAt?: string; finishedAt?: string; durationMs?: number; link?: string; success?: boolean; note?: string; query?: { search?: string; targetId?: number; status?: string }; data?: unknown; limit?: number }
export const conversations = (page: number) => get<{ rows: Conversation[]; total: number; page: number }>(`/production/chat/conversations?page=${page}`)
export const newConversation = () => post<Conversation>('/production/chat/conversations', {})
export const conversationDetail = (id: number, before?: number) => get<{ conversation: Conversation; turns: ChatTurn[]; total: number }>(`/production/chat/conversations/${id}`, before ? { before } : undefined)
export const sendChat = (id: number, text: string, requestKey: string) => post<ChatTurn>(`/production/chat/conversations/${id}/messages`, { text, requestKey }, 160000)
