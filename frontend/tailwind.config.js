/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: 'var(--color-primary)',
          hover: 'var(--color-primary-hover)',
          soft: 'var(--color-primary-soft)',
        },
        ink: 'var(--color-ink)',
        body: 'var(--color-text)',
        muted: 'var(--color-text-muted)',
        canvas: 'var(--color-bg)',
        surface: 'var(--color-surface)',
        border: 'var(--color-border)',
        completed: {
          DEFAULT: 'var(--color-completed)',
          soft: 'var(--color-completed-soft)',
        },
        pending: {
          DEFAULT: 'var(--color-pending)',
          soft: 'var(--color-pending-soft)',
        },
        skipped: {
          DEFAULT: 'var(--color-skipped)',
          soft: 'var(--color-skipped-soft)',
        },
      },
      fontFamily: {
        sans: ['var(--font-sans)'],
      },
      borderRadius: {
        card: 'var(--radius-card)',
        'card-lg': 'var(--radius-card-lg)',
        pill: 'var(--radius-pill)',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        'card-hover': 'var(--shadow-card-hover)',
        drawer: 'var(--shadow-drawer)',
      },
      transitionDuration: {
        fast: 'var(--duration-fast)',
        base: 'var(--duration-base)',
      },
    },
  },
  plugins: [],
};
