export type RetryableCommand = { fingerprint: string; key: string }

export const sameCommand = (current: RetryableCommand | undefined, fingerprint: string): RetryableCommand =>
  current?.fingerprint === fingerprint ? current : { fingerprint, key: crypto.randomUUID() }

export async function submitRetryableCommand<T>(
  state: { value: RetryableCommand | undefined }, fingerprint: string, submit: (key: string) => Promise<T>,
): Promise<T> {
  const command = sameCommand(state.value, fingerprint)
  state.value = command
  const result = await submit(command.key)
  state.value = undefined
  return result
}
