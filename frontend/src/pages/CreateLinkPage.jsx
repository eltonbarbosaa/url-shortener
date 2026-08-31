import { useState } from 'react'
import { Link } from 'react-router-dom'
import { createLink } from '../api/client'

export default function CreateLinkPage() {
  const [originalUrl, setOriginalUrl] = useState('')
  const [customAlias, setCustomAlias] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setResult(null)
    setCopied(false)
    setLoading(true)
    try {
      const response = await createLink(originalUrl, customAlias)
      setResult(response)
      setOriginalUrl('')
      setCustomAlias('')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(result.shortUrl)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="page">
      <header className="app-header">
        <span className="app-header__logo">🔗</span>
        <div>
          <h1 className="app-header__title">Encurtador de Links</h1>
          <p className="app-header__subtitle">Crie links curtos e acompanhe os cliques em tempo real</p>
        </div>
      </header>

      <div className="card">
        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="originalUrl">URL original</label>
            <input
              id="originalUrl"
              type="url"
              required
              value={originalUrl}
              onChange={(e) => setOriginalUrl(e.target.value)}
              placeholder="https://exemplo.com/pagina-muito-longa"
            />
          </div>
          <div className="field">
            <label htmlFor="customAlias">Alias personalizado (opcional)</label>
            <input
              id="customAlias"
              type="text"
              value={customAlias}
              onChange={(e) => setCustomAlias(e.target.value)}
              placeholder="promo2026"
            />
            <span className="field-hint">3 a 32 caracteres: letras, números, - ou _</span>
          </div>
          <button className="btn" type="submit" disabled={loading}>
            {loading ? 'Encurtando...' : 'Encurtar'}
          </button>
        </form>

        {error && <p className="alert-error" role="alert">{error}</p>}

        {result && (
          <div className="result-box">
            <p className="result-box__label">Link criado com sucesso</p>
            <div className="result-link-row">
              <a href={result.shortUrl} target="_blank" rel="noreferrer">{result.shortUrl}</a>
            </div>
            <div className="result-actions">
              <button type="button" className="btn btn-secondary" onClick={handleCopy}>
                {copied ? 'Copiado!' : 'Copiar link'}
              </button>
              <Link to={`/stats/${result.shortCode}`} className="btn btn-secondary">
                Ver estatísticas
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
