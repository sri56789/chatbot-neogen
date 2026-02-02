import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'PDF Chatbot',
  description: 'Chat with your PDF documents',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  )
}



