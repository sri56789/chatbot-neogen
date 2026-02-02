/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  distDir: 'out',
  images: {
    unoptimized: true,
  },
  // Set base path if needed (empty for root)
  basePath: '',
  // Trailing slash for better compatibility
  trailingSlash: false,
}

module.exports = nextConfig



