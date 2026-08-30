const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export async function createLink(originalUrl, customAlias) {
  const response = await fetch(`${API_BASE_URL}/api/links`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ originalUrl, customAlias: customAlias || undefined })
  })
  const data = await response.json()
  if (!response.ok) {
    throw new Error(data.error || 'Failed to create link')
  }
  return data
}

export async function getStats(shortCode) {
  const response = await fetch(`${API_BASE_URL}/api/links/${shortCode}/stats`)
  const data = await response.json()
  if (!response.ok) {
    throw new Error(data.error || 'Failed to load stats')
  }
  return data
}
