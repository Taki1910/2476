import { ApiError } from './api'
import { sameCommand, type RetryableCommand } from './retryable-command'

export type ManualReconciliationState = { command?: RetryableCommand }

export async function submitManualReconciliation<T>(
  state: ManualReconciliationState,
  attemptId: string,
  submit: (idempotencyKey: string) => Promise<T>,
): Promise<T> {
  const command = sameCommand(state.command, attemptId)
  state.command = command
  try {
    const result = await submit(command.key)
    state.command = undefined
    return result
  } catch (error) {
    const sameKeyRetry = error instanceof ApiError
      && error.code === 'CONCURRENCY_RETRY_REQUIRED'
      && error.retryable && error.reuseSameIdempotencyKey
    if (!sameKeyRetry) state.command = undefined
    throw error
  }
}
