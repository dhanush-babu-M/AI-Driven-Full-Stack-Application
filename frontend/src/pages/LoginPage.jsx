import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { PaperPlaneIcon } from '../components/BrandMark';
import {
  APP_NAME,
  APP_QUOTE,
  LOGIN_TAGLINE,
} from '../config/app';
import { useAuth } from '../context/AuthContext';
import {
  getPasswordStrength,
  validateRegisterField,
  validateRegisterForm,
} from '../utils/authValidation';

function EyeIcon({ open }) {
  return open ? (
    <svg
      className="h-4 w-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M17.94 17.94A10.94 10.94 0 0 1 12 20c-7 0-11-8-11-8a21.8 21.8 0 0 1 5.06-5.94" />
      <path d="M9.9 4.24A10.94 10.94 0 0 1 12 4c7 0 11 8 11 8a21.8 21.8 0 0 1-2.16 3.19" />
      <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
      <path d="M1 1l22 22" />
    </svg>
  ) : (
    <svg
      className="h-4 w-4"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function FieldIcon({ name }) {
  const props = {
    className: 'h-4 w-4 text-slate-500',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: '2',
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    'aria-hidden': 'true',
  };

  switch (name) {
    case 'user':
      return (
        <svg {...props}>
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
      );
    case 'phone':
      return (
        <svg {...props}>
          <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.86 19.86 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.86 19.86 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.12.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.58 2.81.7A2 2 0 0 1 22 16.92z" />
        </svg>
      );
    case 'mail':
      return (
        <svg {...props}>
          <rect x="2" y="4" width="20" height="16" rx="2" />
          <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
        </svg>
      );
    case 'lock':
      return (
        <svg {...props}>
          <rect x="3" y="11" width="18" height="11" rx="2" />
          <path d="M7 11V7a5 5 0 0 1 10 0v4" />
        </svg>
      );
    case 'shield':
      return (
        <svg {...props}>
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
          <path d="m9 12 2 2 4-4" />
        </svg>
      );
    default:
      return null;
  }
}

function MountainIllustration() {
  return (
    <svg
      className="w-full max-w-[220px] h-auto mx-auto"
      viewBox="0 0 240 190"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      {/* Soft sky backdrop circle */}
      <circle cx="120" cy="96" r="78" fill="#EFF6FF" />
      <circle cx="164" cy="54" r="14" fill="#DBEAFE" />

      {/* Distant soft ridge */}
      <path
        d="M36 144L84 82L122 122L152 94L204 144H36Z"
        fill="#DBEAFE"
      />

      {/* Main mountain peak */}
      <path
        d="M52 148L118 48L188 148H52Z"
        fill="#BFDBFE"
      />

      {/* Shaded right slope */}
      <path
        d="M118 48L188 148H132L118 48Z"
        fill="#93C5FD"
      />

      {/* Snow / summit highlight */}
      <path
        d="M118 48L102 72L112 68L119 76L127 67L134 71L118 48Z"
        fill="#FFFFFF"
      />

      {/* Winding climb path */}
      <path
        d="M96 148C105 135 123 132 117 118C111 105 128 96 121 82"
        stroke="#2563EB"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeDasharray="4 4"
      />

      {/* Flagpole and flag at the summit */}
      <path
        d="M118 48V20"
        stroke="#1D4ED8"
        strokeWidth="2.5"
        strokeLinecap="round"
      />
      <path
        d="M119 21L146 28.5L119 36V21Z"
        fill="#2563EB"
      />

      {/* Ground baseline */}
      <path
        d="M28 148H212"
        stroke="#93C5FD"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  );
}

const INITIAL_REGISTER_VALUES = {
  fullName: '',
  phone: '',
  email: '',
  password: '',
  confirmPassword: '',
};

const REGISTER_FIELDS = [
  {
    name: 'fullName',
    label: 'Full Name',
    type: 'text',
    icon: 'user',
    placeholder: 'Alex Johnson',
    autoComplete: 'name',
  },
  {
    name: 'phone',
    label: 'Phone Number',
    type: 'tel',
    inputMode: 'numeric',
    icon: 'phone',
    placeholder: '9876543210',
    autoComplete: 'tel',
  },
  {
    name: 'email',
    label: 'Email Address',
    type: 'email',
    icon: 'mail',
    placeholder: 'alex@example.com',
    autoComplete: 'email',
  },
  {
    name: 'password',
    label: 'Password',
    type: 'password',
    icon: 'lock',
    placeholder: 'Minimum 8 characters',
    autoComplete: 'new-password',
    toggleable: true,
  },
  {
    name: 'confirmPassword',
    label: 'Confirm Password',
    type: 'password',
    icon: 'shield',
    placeholder: 'Re-enter your password',
    autoComplete: 'new-password',
    toggleable: true,
  },
];

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, register } = useAuth();

  // Mobile tab state (< md breakpoint)
  const [mobileTab, setMobileTab] = useState('login');

  // Login form state (never persisted)
  const [identifier, setIdentifier] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [showLoginPassword, setShowLoginPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [loginError, setLoginError] = useState('');

  // Register form state (never persisted)
  const [regValues, setRegValues] = useState(INITIAL_REGISTER_VALUES);
  const [regErrors, setRegErrors] = useState({});
  const [regTouched, setRegTouched] = useState({});
  const [showRegPassword, setShowRegPassword] = useState(false);
  const [showRegConfirm, setShowRegConfirm] = useState(false);
  const [isRegistering, setIsRegistering] = useState(false);
  const [registerNotice, setRegisterNotice] = useState('');
  const [registerServerError, setRegisterServerError] = useState('');

  const loginInputRef = useRef(null);
  const loginPasswordRef = useRef(null);
  const registerInputRef = useRef(null);

  const passwordStrength = getPasswordStrength(regValues.password);

  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    if (isLoggingIn) return;

    const trimmedIdentifier = identifier.trim();
    if (!trimmedIdentifier || !loginPassword) {
      setLoginError('Please enter your email or phone and password.');
      return;
    }

    setLoginError('');
    setIsLoggingIn(true);
    try {
      await login(trimmedIdentifier, loginPassword);
      setLoginPassword('');
      navigate('/welcome', { replace: true });
    } catch (err) {
      setLoginError(err?.message || 'Unable to sign in. Please try again.');
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleRegChange = (field, nextValue) => {
    const updated = { ...regValues, [field]: nextValue };
    setRegValues(updated);
    setRegisterNotice('');
    setRegisterServerError('');

    if (regTouched[field]) {
      setRegErrors((prev) => ({
        ...prev,
        [field]: validateRegisterField(field, updated),
      }));
    }

    // Re-validate confirmPassword live when password changes after confirm was touched
    if (field === 'password' && regTouched.confirmPassword) {
      setRegErrors((prev) => ({
        ...prev,
        confirmPassword: validateRegisterField('confirmPassword', updated),
      }));
    }
  };

  const handleRegBlur = (field) => {
    setRegTouched((prev) => ({ ...prev, [field]: true }));
    setRegErrors((prev) => ({
      ...prev,
      [field]: validateRegisterField(field, regValues),
    }));
  };

  const handleRegisterSubmit = async (e) => {
    e.preventDefault();
    if (isRegistering) return;

    const allTouched = {
      fullName: true,
      phone: true,
      email: true,
      password: true,
      confirmPassword: true,
    };
    setRegTouched(allTouched);
    setRegisterServerError('');
    setRegisterNotice('');

    const validationErrors = validateRegisterForm(regValues);
    setRegErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) {
      return;
    }

    setIsRegistering(true);
    try {
      const created = await register({
        fullName: regValues.fullName.trim(),
        phone: regValues.phone.trim(),
        email: regValues.email.trim(),
        password: regValues.password,
        confirmPassword: regValues.confirmPassword,
      });

      setRegValues(INITIAL_REGISTER_VALUES);
      setRegTouched({});
      setRegErrors({});
      setIdentifier(created?.email || '');
      setLoginError('');
      setRegisterNotice('Account created! Sign in on the Login form to continue.');
      setMobileTab('login');
      setTimeout(() => {
        loginPasswordRef.current?.focus();
      }, 0);
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors && Object.keys(err.fieldErrors).length > 0) {
        setRegErrors((prev) => ({ ...prev, ...err.fieldErrors }));
      }
      setRegisterServerError(err?.message || 'Unable to create account. Please try again.');
    } finally {
      setIsRegistering(false);
    }
  };

  const focusRegisterForm = () => {
    setMobileTab('register');
    setTimeout(() => {
      registerInputRef.current?.focus();
    }, 0);
  };

  const focusLoginForm = () => {
    setMobileTab('login');
    setTimeout(() => {
      loginInputRef.current?.focus();
    }, 0);
  };

  return (
    <div className="min-h-screen bg-canvas flex items-center justify-center p-4 sm:p-6 lg:p-8">
      <main className="w-full max-w-6xl">
        <h1 className="sr-only">{APP_NAME} — Welcome and Access</h1>

        <div className="bg-surface rounded-card-lg shadow-card border border-border/80 overflow-hidden">
          {/* Mobile quote & tabs (< md) */}
          <div className="md:hidden px-5 pt-5 pb-2 space-y-4">
            <div className="text-center px-2">
              <p className="text-sm font-medium text-ink leading-relaxed">
                &ldquo;{APP_QUOTE}&rdquo;
              </p>
              <div className="mt-2.5 mx-auto h-1 w-12 rounded-full bg-primary" />
            </div>

            <div
              role="tablist"
              aria-label="Account access mode"
              className="grid grid-cols-2 rounded-pill bg-canvas p-1 border border-border"
            >
              <button
                type="button"
                role="tab"
                id="tab-login"
                aria-selected={mobileTab === 'login'}
                aria-controls="panel-login"
                onClick={() => setMobileTab('login')}
                className={`py-2 text-sm font-semibold rounded-pill transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                  mobileTab === 'login'
                    ? 'bg-primary text-white shadow-sm'
                    : 'text-slate-700 hover:text-ink'
                }`}
              >
                Login
              </button>
              <button
                type="button"
                role="tab"
                id="tab-register"
                aria-selected={mobileTab === 'register'}
                aria-controls="panel-register"
                onClick={() => setMobileTab('register')}
                className={`py-2 text-sm font-semibold rounded-pill transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                  mobileTab === 'register'
                    ? 'bg-primary text-white shadow-sm'
                    : 'text-slate-700 hover:text-ink'
                }`}
              >
                Register
              </button>
            </div>
          </div>

          {/* 3-column grid on tablet & desktop (md+), single active tab on mobile */}
          <div className="p-5 sm:p-6 md:p-6 lg:p-10 md:grid md:grid-cols-[minmax(0,1fr)_minmax(180px,0.8fr)_minmax(0,1.08fr)] md:gap-5 lg:gap-8 items-stretch">
            {/* LEFT COLUMN: LOGIN */}
            <section
              id="panel-login"
              aria-labelledby="login-heading"
              className={`min-w-0 flex flex-col justify-center ${
                mobileTab === 'login' ? 'block' : 'hidden md:flex'
              }`}
            >
              <div className="flex items-center gap-2.5 mb-4">
                <PaperPlaneIcon
                  className="h-5 w-5"
                  wrapperClassName="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary text-white shadow-sm"
                />
                <div>
                  <div className="text-base font-bold text-ink leading-tight">
                    {APP_NAME}
                  </div>
                  <div className="text-xs font-medium text-slate-600">
                    {LOGIN_TAGLINE}
                  </div>
                </div>
              </div>

              <h2 id="login-heading" className="text-2xl font-bold text-ink">
                Login
              </h2>
              <p className="mt-1 text-sm text-slate-600">
                Welcome back! Please sign in to continue.
              </p>

              {loginError && (
                <div
                  role="alert"
                  className="mt-4 rounded-xl border border-red-200 bg-skipped-soft px-3.5 py-2.5 text-xs sm:text-sm font-medium text-red-700"
                >
                  {loginError}
                </div>
              )}

              <form onSubmit={handleLoginSubmit} noValidate className="mt-5 space-y-4">
                <div>
                  <label
                    htmlFor="login-identifier"
                    className="block text-sm font-medium text-ink mb-1.5"
                  >
                    Email or Phone
                  </label>
                  <input
                    ref={loginInputRef}
                    id="login-identifier"
                    name="identifier"
                    type="text"
                    autoComplete="username"
                    value={identifier}
                    onChange={(e) => {
                      setIdentifier(e.target.value);
                      if (loginError) setLoginError('');
                    }}
                    placeholder="you@example.com or 9876543210"
                    className="w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-sm text-ink placeholder:text-slate-500 focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/25 transition-colors duration-fast"
                  />
                </div>

                <div>
                  <label
                    htmlFor="login-password"
                    className="block text-sm font-medium text-ink mb-1.5"
                  >
                    Password
                  </label>
                  <div className="relative">
                    <input
                      ref={loginPasswordRef}
                      id="login-password"
                      name="loginPassword"
                      type={showLoginPassword ? 'text' : 'password'}
                      autoComplete="current-password"
                      value={loginPassword}
                      onChange={(e) => {
                        setLoginPassword(e.target.value);
                        if (loginError) setLoginError('');
                      }}
                      placeholder="Enter your password"
                      className="w-full rounded-xl border border-border bg-surface pl-3.5 pr-11 py-2.5 text-sm text-ink placeholder:text-slate-500 focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/25 transition-colors duration-fast"
                    />
                    <button
                      type="button"
                      onClick={() => setShowLoginPassword((prev) => !prev)}
                      aria-label={showLoginPassword ? 'Hide password' : 'Show password'}
                      aria-pressed={showLoginPassword}
                      className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-600 hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-r-xl"
                    >
                      <EyeIcon open={showLoginPassword} />
                    </button>
                  </div>
                </div>

                <div className="flex items-center justify-between gap-2 text-sm">
                  <label
                    htmlFor="login-remember"
                    className="inline-flex items-center gap-2 text-slate-700 cursor-pointer select-none"
                  >
                    <input
                      id="login-remember"
                      type="checkbox"
                      checked={rememberMe}
                      onChange={(e) => setRememberMe(e.target.checked)}
                      className="h-4 w-4 rounded border-border text-primary focus:ring-2 focus:ring-primary/30"
                    />
                    <span>Remember me</span>
                  </label>

                  <span className="relative group inline-flex items-center">
                    <button
                      type="button"
                      aria-disabled="true"
                      title="Coming soon"
                      onClick={(e) => e.preventDefault()}
                      className="text-xs sm:text-sm font-medium text-slate-600 cursor-not-allowed underline decoration-dotted underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded px-1 py-0.5"
                    >
                      Forgot password?
                    </button>
                    <span
                      role="tooltip"
                      className="pointer-events-none opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 transition-opacity duration-fast absolute bottom-full right-0 mb-1.5 whitespace-nowrap rounded-lg bg-ink px-2.5 py-1 text-xs font-medium text-white shadow-card"
                    >
                      Coming soon
                    </span>
                  </span>
                </div>

                <button
                  type="submit"
                  disabled={isLoggingIn}
                  className="w-full rounded-pill bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-primary-hover disabled:opacity-60 disabled:cursor-not-allowed transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
                >
                  {isLoggingIn ? 'Signing in...' : 'Login'}
                </button>
              </form>

              <p className="mt-5 text-center text-sm text-slate-600">
                Don&apos;t have an account?{' '}
                <button
                  type="button"
                  onClick={focusRegisterForm}
                  className="font-semibold text-primary hover:text-primary-hover underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded px-1"
                >
                  Register
                </button>
              </p>
            </section>

            {/* CENTRE COLUMN: ILLUSTRATION & QUOTE (Tablet & Desktop) */}
            <aside
              aria-label="Daily motivation"
              className="hidden md:flex flex-col items-center justify-center text-center border-x border-border/70 px-4 lg:px-6"
            >
              <MountainIllustration />
              <blockquote className="mt-5 text-sm lg:text-base font-medium text-ink leading-relaxed">
                &ldquo;{APP_QUOTE}&rdquo;
              </blockquote>
              <div className="mt-3.5 h-1 w-14 rounded-full bg-primary" aria-hidden="true" />
            </aside>

            {/* RIGHT COLUMN: REGISTER */}
            <section
              id="panel-register"
              aria-labelledby="register-heading"
              className={`min-w-0 flex flex-col justify-center ${
                mobileTab === 'register' ? 'block' : 'hidden md:flex'
              }`}
            >
              <h2 id="register-heading" className="text-2xl font-bold text-ink">
                Register
              </h2>
              <p className="mt-1 text-sm text-slate-600">
                Create your account to get started.
              </p>

              {registerNotice && (
                <div
                  role="status"
                  className="mt-4 rounded-xl border border-completed/30 bg-completed-soft px-3.5 py-2.5 text-xs sm:text-sm font-medium text-teal-900"
                >
                  {registerNotice}
                </div>
              )}

              {registerServerError && (
                <div
                  role="alert"
                  className="mt-4 rounded-xl border border-red-200 bg-skipped-soft px-3.5 py-2.5 text-xs sm:text-sm font-medium text-red-700"
                >
                  {registerServerError}
                </div>
              )}

              <form onSubmit={handleRegisterSubmit} noValidate className="mt-4 space-y-3.5">
                {REGISTER_FIELDS.map((field, index) => {
                  const inputId = `reg-${field.name}`;
                  const errorId = `${inputId}-error`;
                  const hintId = field.name === 'password' ? `${inputId}-hint` : '';
                  const errorMsg = regErrors[field.name] || '';

                  const isPasswordField = field.name === 'password';
                  const isConfirmField = field.name === 'confirmPassword';
                  const isVisible = isPasswordField
                    ? showRegPassword
                    : isConfirmField
                      ? showRegConfirm
                      : false;

                  const inputType = field.toggleable
                    ? isVisible
                      ? 'text'
                      : 'password'
                    : field.type;

                  const describedBy =
                    [hintId, errorMsg ? errorId : ''].filter(Boolean).join(' ') || undefined;

                  return (
                    <div key={field.name}>
                      <label
                        htmlFor={inputId}
                        className="block text-sm font-medium text-ink mb-1"
                      >
                        {field.label}
                      </label>
                      <div className="relative">
                        <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
                          <FieldIcon name={field.icon} />
                        </span>
                        <input
                          ref={index === 0 ? registerInputRef : undefined}
                          id={inputId}
                          name={field.name}
                          type={inputType}
                          inputMode={field.inputMode}
                          autoComplete={field.autoComplete}
                          value={regValues[field.name]}
                          onChange={(e) => handleRegChange(field.name, e.target.value)}
                          onBlur={() => handleRegBlur(field.name)}
                          placeholder={field.placeholder}
                          aria-invalid={Boolean(errorMsg)}
                          aria-describedby={describedBy}
                          className={`w-full rounded-xl border bg-surface pl-10 ${
                            field.toggleable ? 'pr-11' : 'pr-3.5'
                          } py-2.5 text-sm text-ink placeholder:text-slate-500 focus:outline-none focus:ring-2 transition-colors duration-fast ${
                            errorMsg
                              ? 'border-red-600 focus:border-red-600 focus:ring-red-600/20'
                              : 'border-border focus:border-primary focus:ring-primary/25'
                          }`}
                        />
                        {field.toggleable && (
                          <button
                            type="button"
                            onClick={() =>
                              isPasswordField
                                ? setShowRegPassword((prev) => !prev)
                                : setShowRegConfirm((prev) => !prev)
                            }
                            aria-label={
                              isVisible
                                ? `Hide ${field.label.toLowerCase()}`
                                : `Show ${field.label.toLowerCase()}`
                            }
                            aria-pressed={isVisible}
                            className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-600 hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-r-xl"
                          >
                            <EyeIcon open={isVisible} />
                          </button>
                        )}
                      </div>

                      {isPasswordField && (
                        <div id={hintId} className="mt-1.5 space-y-1">
                          <div className="h-1.5 w-full rounded-full bg-canvas overflow-hidden">
                            <div
                              className={`h-full transition-all duration-base ${passwordStrength.barClass}`}
                              style={{ width: `${passwordStrength.percent}%` }}
                            />
                          </div>
                          <p className={`text-xs font-medium ${passwordStrength.toneClass}`}>
                            {passwordStrength.label ||
                              'Use at least 8 characters with 1 letter and 1 number.'}
                          </p>
                        </div>
                      )}

                      {errorMsg && (
                        <p id={errorId} role="alert" className="mt-1 text-xs font-medium text-red-700">
                          {errorMsg}
                        </p>
                      )}
                    </div>
                  );
                })}

                <button
                  type="submit"
                  disabled={isRegistering}
                  className="w-full rounded-pill bg-primary px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-primary-hover disabled:opacity-60 disabled:cursor-not-allowed transition-colors duration-fast focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
                >
                  {isRegistering ? 'Creating Account...' : 'Create Account'}
                </button>
              </form>

              <p className="mt-4 text-center text-sm text-slate-600">
                Already have an account?{' '}
                <button
                  type="button"
                  onClick={focusLoginForm}
                  className="font-semibold text-primary hover:text-primary-hover underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded px-1"
                >
                  Login
                </button>
              </p>
            </section>
          </div>
        </div>
      </main>
    </div>
  );
}
