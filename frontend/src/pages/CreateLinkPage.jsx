import { useState } from 'react'
import { Link } from 'react-router-dom'
import { createLink } from '../api/client'

export default function CreateLinkPage() {
  const [originalUrl, setOriginalUrl] = useState('')
  const [customAlias, setCustomAlias] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setResult(null)
    try {
      const response = await createLink(originalUrl, customAlias)
      setResult(response)
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div>
      <h1>Encurtador de Links</h1>
      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="originalUrl">URL original</label>
          <input
            id="originalUrl"
            type="url"
            required
            value={originalUrl}
            onChange={(e) => setOriginalUrl(e.target.value)}
            placeholder="https://exemplo.com/pagina"
          />
        </div>
        <div>
          <label htmlFor="customAlias">Alias personalizado (opcional)</label>
          <input
            id="customAlias"
            type="text"
            value={customAlias}
            onChange={(e) => setCustomAlias(e.target.value)}
            placeholder="promo2026"
          />
        </div>
        <button type="submit">Encurtar</button>
      </form>

      {error && <p role="alert">{error}</p>}

      {result && (
        <div>
          <p>
            Link criado: <a href={result.shortUrl}>{result.shortUrl}</a>
          </p>
          <Link to={`/stats/${result.shortCode}`}>Ver estatísticas</Link>
        </div>
      )}
    </div>
  )
}
