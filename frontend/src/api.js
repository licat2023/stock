// Handles same-origin JSON requests and preserves error messages from the API.

/** Returns a JSON response or rejects with the user-facing API error and its HTTP status. */
export async function request(path, { method = 'GET', body, signal } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(`/api/${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new Error('无法连接到进销存服务，请检查服务后重试。')
  }

  if (response.status === 204) return null

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(payload?.message || `请求失败（${response.status}），请重试。`)
    error.status = response.status
    throw error
  }
  if (payload === null) throw new Error('服务返回的数据格式不正确，请重试。')
  return payload
}
