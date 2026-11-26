import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PaperPlaneIcon } from '../components/BrandMark';
import { APP_NAME, WELCOME_FLAG_KEY } from '../config/app';

export default function WelcomePage() {
  const navigate = useNavigate();
  const hasNavigatedRef = useRef(false);

  // Read one-time flag once on mount so normal navigation or refresh never replays.
  const [canPlay] = useState(() => sessionStorage.getItem(WELCOME_FLAG_KEY) === '1');
  const [reducedMotion] = useState(
    () =>
      typeof window !== 'undefined' &&
      Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
  );

  const goToDashboard = useCallback(() => {
    if (hasNavigatedRef.current) return;
    hasNavigatedRef.current = true;
    navigate('/app/dashboard', { replace: true });
  }, [navigate]);

  useEffect(() => {
    if (!canPlay) {
      goToDashboard();
      return undefined;
    }

    // Clear the flag as soon as playback starts so a page refresh cannot replay it.
    sessionStorage.removeItem(WELCOME_FLAG_KEY);

    const totalMs = reducedMotion ? 1000 : 2100;
    const timer = window.setTimeout(goToDashboard, totalMs);

    const handleKeyDown = (event) => {
      if (event.key === 'Escape' || event.key === 'Enter') {
        event.preventDefault();
        goToDashboard();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [canPlay, reducedMotion, goToDashboard]);

  if (!canPlay) {
    return <div className="min-h-screen bg-[#0B1220]" aria-hidden="true" />;
  }

  return (
    <main className="relative min-h-screen w-full overflow-hidden bg-[#0B1220] text-white flex flex-col justify-between p-6 sm:p-10 lg:p-14">
      {/* Soft blue glow on the right */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-28 top-1/2 -translate-y-1/2 h-[420px] w-[420px] sm:h-[560px] sm:w-[560px] rounded-full opacity-35 blur-3xl"
        style={{
          background:
            'radial-gradient(circle, rgba(37, 99, 235, 0.75) 0%, rgba(37, 99, 235, 0) 70%)',
        }}
      />

      {/* Top row: Skip action */}
      <div className="relative z-10 flex justify-end">
        <button
          type="button"
          onClick={goToDashboard}
          className="rounded-pill border border-white/20 bg-white/5 px-4 py-1.5 text-sm font-medium text-slate-200 hover:bg-white/15 hover:text-white transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-[#0B1220]"
        >
          Skip
        </button>
      </div>

      {/* Left-middle hero content (0.0-0.4s dark, 0.4-1.2s fade-in unless reduced-motion) */}
      <section
        aria-label="Welcome message"
        className={`relative z-10 my-auto max-w-2xl ${
          reducedMotion ? '' : 'welcome-content-reveal'
        }`}
      >
        <PaperPlaneIcon
          className="h-7 w-7 text-blue-400"
          wrapperClassName="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/20 border border-blue-400/30 mb-6"
        />

        <p className="text-xs font-semibold uppercase tracking-[0.28em] text-blue-300">
          WELCOME
        </p>

        <h1 className="mt-2 text-4xl sm:text-6xl font-bold tracking-tight text-white">
          {APP_NAME}
        </h1>

        <p className="mt-4 text-base sm:text-lg text-slate-300 font-normal">
          Track Your Skills . Build Your Career . Achieve Your Goals
        </p>
      </section>

      {/* Bottom row: thin blue loading bar (left) + quote (right) */}
      <footer className="relative z-10 flex flex-col sm:flex-row sm:items-end justify-between gap-4 pt-8">
        <div
          aria-hidden="true"
          className="w-52 sm:w-64 h-1 rounded-full bg-white/15 overflow-hidden"
        >
          <div
            className={`h-full w-full bg-primary rounded-full ${
              reducedMotion ? '' : 'welcome-progress-fill'
            }`}
          />
        </div>

        <p className="text-xs sm:text-sm italic text-slate-300">
          Your future is a journey, not a destination.
        </p>
      </footer>
    </main>
  );
}
