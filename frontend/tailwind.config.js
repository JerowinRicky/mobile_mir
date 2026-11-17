/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          blue: '#2563EB',
          emerald: '#10B981',
          danger: '#EF4444',
          dark: '#0F172A',
          card: '#1E293B',
          cardLight: '#334155'
        }
      }
    },
  },
  plugins: [],
}
