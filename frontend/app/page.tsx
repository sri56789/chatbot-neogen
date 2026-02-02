'use client';
import { useState, useRef, useEffect } from 'react';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  images?: string[];
}

interface Status {
  chunksLoaded: number;
  ready: boolean;
  indexing: boolean;
  lastIndexError?: string | null;
  lastIndexedAt?: number | null;
  catalogEnabled?: boolean;
  catalogProducts?: number;
}

export default function Home() {
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<Status | null>(null);
  const [selectedImages, setSelectedImages] = useState<string[] | null>(null);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [zoom, setZoom] = useState(1);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  useEffect(() => {
    let isMounted = true;
    let interval: ReturnType<typeof setInterval> | null = null;

    const fetchStatus = async () => {
      try {
        const res = await fetch('/api/status');
        if (!res.ok) return;
        const data = await res.json();
        if (isMounted) {
          setStatus(data);
          if (data.ready && !data.indexing && interval) {
            clearInterval(interval);
            interval = null;
          }
        }
      } catch {
        if (isMounted) {
          setStatus(prev => prev ?? { chunksLoaded: 0, ready: false, indexing: false });
        }
      }
    };

    fetchStatus();
    interval = setInterval(fetchStatus, 5000);
    return () => {
      isMounted = false;
      if (interval) {
        clearInterval(interval);
      }
    };
  }, []);

  const ask = async () => {
    if (!input.trim() || loading) return;

    const userMessage: Message = { role: 'user', content: input };
    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setLoading(true);

    try {
      // Use relative URL since we're served from the same server
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: userMessage.content })
      });
      
      if (!res.ok) {
        throw new Error('Failed to get response');
      }
      
      const data = await res.json();
      const assistantMessage: Message = { role: 'assistant', content: data.answer, images: data.images };
      setMessages(prev => [...prev, assistantMessage]);
    } catch (error) {
      const errorMessage: Message = { 
        role: 'assistant', 
        content: 'Sorry, I encountered an error. Please make sure the backend is running and PDFs are indexed.' 
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      ask();
    }
  };

  const reloadDocuments = async () => {
    try {
      // Use relative URL since we're served from the same server
      const res = await fetch('/api/reload', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });
      if (res.ok) {
        const data = await res.json();
        alert(`PDFs reindexed successfully! Indexed ${data.chunks || 0} text chunks.`);
      } else {
        const data = await res.json();
        alert(`Failed to reindex PDFs: ${data.status || 'Unknown error'}`);
      }
    } catch (error) {
      alert('Failed to reindex PDFs. Make sure the backend is running on port 8080.');
    }
  };

  const openImageViewer = (images: string[], index: number) => {
    setSelectedImages(images);
    setSelectedIndex(index);
    setZoom(1);
  };

  const closeImageViewer = () => {
    setSelectedImages(null);
    setSelectedIndex(0);
    setZoom(1);
  };

  const showPrevImage = () => {
    if (!selectedImages || selectedImages.length === 0) return;
    setSelectedIndex((prev) => (prev - 1 + selectedImages.length) % selectedImages.length);
    setZoom(1);
  };

  const showNextImage = () => {
    if (!selectedImages || selectedImages.length === 0) return;
    setSelectedIndex((prev) => (prev + 1) % selectedImages.length);
    setZoom(1);
  };

  const zoomIn = () => setZoom((z) => Math.min(3, Math.round((z + 0.25) * 100) / 100));
  const zoomOut = () => setZoom((z) => Math.max(1, Math.round((z - 0.25) * 100) / 100));
  const resetZoom = () => setZoom(1);

  return (
    <main style={styles.container}>
      <div style={styles.header}>
        <div style={styles.titleBlock}>
          <h1 style={styles.title}>📄 PDF Chatbot</h1>
          {status && (
            <div style={styles.statusRow}>
              <span
                style={{
                  ...styles.statusDot,
                  ...(status.indexing
                    ? styles.statusDotYellow
                    : status.ready
                    ? styles.statusDotGreen
                    : styles.statusDotRed)
                }}
              />
              <span style={styles.statusText}>
                {status.indexing
                  ? 'Indexing PDFs...'
                  : status.ready
                  ? status.catalogEnabled
                    ? `Ready (${status.catalogProducts ?? 0} products)`
                    : `Ready (${status.chunksLoaded} chunks)`
                  : status.catalogEnabled
                  ? 'Catalog not indexed'
                  : 'Not indexed'}
              </span>
            </div>
          )}
          {status?.lastIndexError && (
            <div style={styles.statusError}>Index error: {status.lastIndexError}</div>
          )}
        </div>
        <button
          onClick={reloadDocuments}
          style={{
            ...styles.reloadButton,
            ...(status?.indexing ? styles.reloadButtonDisabled : {})
          }}
          disabled={status?.indexing}
        >
          🔄 Reindex PDFs
        </button>
      </div>

      <div style={styles.chatContainer}>
        {messages.length === 0 && (
          <div style={styles.welcomeMessage}>
            <p>👋 Welcome! Ask me anything about the PDFs in the <code>pdfs</code> folder.</p>
            <p style={styles.hint}>PDFs are indexed when the server starts. Add files to <code>pdfs</code> and click "Reindex PDFs" if you add new ones.</p>
          </div>
        )}
        
        {messages.map((msg, idx) => (
          <div
            key={idx}
            style={{
              ...styles.message,
              ...(msg.role === 'user' ? styles.userMessage : styles.assistantMessage)
            }}
          >
            <div style={styles.messageContent}>
              <strong>{msg.role === 'user' ? 'You' : 'Assistant'}:</strong>
              <div style={styles.messageText}>{msg.content}</div>
              {msg.images && msg.images.length > 0 && (
                <div style={styles.imageGrid}>
                  {msg.images.map((src, imgIdx) => (
                    <img
                      key={imgIdx}
                      src={src}
                      alt="Related catalog item"
                      style={styles.imageThumb}
                      onClick={() => openImageViewer(msg.images || [], imgIdx)}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
        
        {loading && (
          <div style={{ ...styles.message, ...styles.assistantMessage }}>
            <div style={styles.messageContent}>
              <strong>Assistant:</strong>
              <div style={styles.messageText}>Thinking...</div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div style={styles.inputContainer}>
        <div style={styles.inputColumn}>
          <textarea
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="Ask a question about your PDFs..."
            style={styles.textarea}
            rows={3}
            disabled={loading || status?.indexing}
          />
        </div>
        <button
          onClick={ask}
          disabled={loading || !input.trim() || status?.indexing}
          style={{
            ...styles.sendButton,
            ...(loading || !input.trim() || status?.indexing ? styles.sendButtonDisabled : {})
          }}
        >
          Send
        </button>
      </div>
      {selectedImages && selectedImages.length > 0 && (
        <div style={styles.modalBackdrop} onClick={closeImageViewer}>
          <div style={styles.modalContent} onClick={e => e.stopPropagation()}>
            <div style={styles.modalToolbar}>
              {selectedImages.length > 1 && (
                <div style={styles.modalNav}>
                  <button style={styles.modalButton} onClick={showPrevImage}>
                    Prev
                  </button>
                  <span style={styles.modalCounter}>
                    {selectedIndex + 1} / {selectedImages.length}
                  </span>
                  <button style={styles.modalButton} onClick={showNextImage}>
                    Next
                  </button>
                </div>
              )}
              <div style={styles.modalZoom}>
                <button style={styles.modalButton} onClick={zoomOut}>
                  -
                </button>
                <span style={styles.modalCounter}>{Math.round(zoom * 100)}%</span>
                <button style={styles.modalButton} onClick={zoomIn}>
                  +
                </button>
                <button style={styles.modalButton} onClick={resetZoom}>
                  Reset
                </button>
              </div>
              <button style={styles.modalClose} onClick={closeImageViewer}>
                Close
              </button>
            </div>
            <div style={styles.modalImageWrap}>
              <img
                src={selectedImages[selectedIndex]}
                alt="Selected catalog item"
                style={{ ...styles.modalImage, transform: `scale(${zoom})` }}
              />
            </div>
          </div>
        </div>
      )}
    </main>
  );
}

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column' as const,
    height: '100vh',
    maxWidth: '1200px',
    margin: '0 auto',
    padding: '20px',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    backgroundColor: '#f5f5f5',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '20px',
    padding: '20px',
    backgroundColor: 'white',
    borderRadius: '10px',
    boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
  },
  title: {
    margin: 0,
    fontSize: '28px',
    color: '#333',
  },
  titleBlock: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '6px',
  },
  statusRow: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    fontSize: '13px',
    color: '#555',
  },
  statusText: {
    fontWeight: 600,
  },
  statusSubtext: {
    color: '#777',
  },
  statusError: {
    color: '#b00020',
    fontSize: '12px',
  },
  statusDot: {
    width: '10px',
    height: '10px',
    borderRadius: '50%',
    display: 'inline-block',
  },
  statusDotGreen: {
    backgroundColor: '#28a745',
  },
  statusDotYellow: {
    backgroundColor: '#f0ad4e',
  },
  statusDotRed: {
    backgroundColor: '#dc3545',
  },
  reloadButton: {
    padding: '10px 20px',
    backgroundColor: '#007bff',
    color: 'white',
    border: 'none',
    borderRadius: '5px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: 'bold',
  },
  reloadButtonDisabled: {
    backgroundColor: '#9bbcf5',
    cursor: 'not-allowed',
  },
  chatContainer: {
    flex: 1,
    overflowY: 'auto' as const,
    padding: '20px',
    backgroundColor: 'white',
    borderRadius: '10px',
    marginBottom: '20px',
    boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
  },
  welcomeMessage: {
    textAlign: 'center' as const,
    color: '#666',
    padding: '40px 20px',
  },
  hint: {
    fontSize: '14px',
    marginTop: '10px',
    color: '#999',
  },
  message: {
    marginBottom: '20px',
    padding: '15px',
    borderRadius: '10px',
    maxWidth: '80%',
  },
  userMessage: {
    backgroundColor: '#007bff',
    color: 'white',
    marginLeft: 'auto',
    textAlign: 'right' as const,
  },
  assistantMessage: {
    backgroundColor: '#e9ecef',
    color: '#333',
    marginRight: 'auto',
  },
  messageContent: {
    wordWrap: 'break-word' as const,
  },
  messageText: {
    marginTop: '5px',
    whiteSpace: 'pre-wrap' as const,
  },
  imageGrid: {
    display: 'flex',
    flexWrap: 'wrap' as const,
    gap: '8px',
    marginTop: '10px',
  },
  imageThumb: {
    width: '160px',
    height: 'auto',
    borderRadius: '6px',
    border: '1px solid #ddd',
    cursor: 'pointer',
  },
  modalBackdrop: {
    position: 'fixed' as const,
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.6)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    padding: '20px',
  },
  modalContent: {
    backgroundColor: '#fff',
    borderRadius: '8px',
    padding: '16px',
    maxWidth: '90vw',
    maxHeight: '90vh',
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '12px',
  },
  modalToolbar: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '12px',
    flexWrap: 'wrap' as const,
  },
  modalNav: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  modalZoom: {
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  modalCounter: {
    fontSize: '14px',
    color: '#444',
  },
  modalButton: {
    padding: '6px 10px',
    borderRadius: '4px',
    border: '1px solid #ccc',
    backgroundColor: '#f8f9fa',
    cursor: 'pointer',
  },
  modalImageWrap: {
    flex: 1,
    overflow: 'auto' as const,
    borderRadius: '6px',
    border: '1px solid #eee',
    padding: '8px',
    backgroundColor: '#fafafa',
  },
  modalImage: {
    maxWidth: '100%',
    maxHeight: '100%',
    objectFit: 'contain' as const,
    borderRadius: '6px',
    transformOrigin: 'top left',
    display: 'block',
  },
  modalClose: {
    alignSelf: 'flex-end',
    padding: '8px 12px',
    borderRadius: '4px',
    border: 'none',
    backgroundColor: '#007bff',
    color: '#fff',
    cursor: 'pointer',
  },
  inputContainer: {
    display: 'flex',
    gap: '10px',
    padding: '20px',
    backgroundColor: 'white',
    borderRadius: '10px',
    boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
  },
  inputColumn: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '10px',
  },
  textarea: {
    flex: 1,
    padding: '15px',
    border: '2px solid #ddd',
    borderRadius: '5px',
    fontSize: '16px',
    fontFamily: 'inherit',
    resize: 'none' as const,
  },
  sendButton: {
    padding: '15px 30px',
    backgroundColor: '#007bff',
    color: 'white',
    border: 'none',
    borderRadius: '5px',
    cursor: 'pointer',
    fontSize: '16px',
    fontWeight: 'bold',
  },
  sendButtonDisabled: {
    backgroundColor: '#ccc',
    cursor: 'not-allowed',
  },
};