const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const DIGITS_ONLY_REGEX = /^\d+$/;
const HAS_LETTER_REGEX = /[A-Za-z]/;
const HAS_NUMBER_REGEX = /\d/;

export function getPasswordStrength(password) {
  if (!password) {
    return { label: '', toneClass: 'text-slate-600', barClass: 'bg-border', percent: 0 };
  }

  let score = 0;
  if (password.length >= 8) score += 1;
  if (HAS_LETTER_REGEX.test(password) && HAS_NUMBER_REGEX.test(password)) score += 1;
  if (password.length >= 12) score += 1;
  if (/[^A-Za-z0-9]/.test(password) || (/[a-z]/.test(password) && /[A-Z]/.test(password))) {
    score += 1;
  }

  if (score <= 1) {
    return {
      label: 'Weak — use 8+ chars with letters and numbers',
      toneClass: 'text-red-700',
      barClass: 'bg-skipped',
      percent: 25,
    };
  }
  if (score === 2) {
    return {
      label: 'Fair — meets minimum requirements',
      toneClass: 'text-amber-700',
      barClass: 'bg-pending',
      percent: 55,
    };
  }
  if (score === 3) {
    return {
      label: 'Good — strong length and mix',
      toneClass: 'text-teal-700',
      barClass: 'bg-completed',
      percent: 80,
    };
  }
  return {
    label: 'Strong password',
    toneClass: 'text-teal-700',
    barClass: 'bg-completed',
    percent: 100,
  };
}

export function validateRegisterField(name, values) {
  const value = values[name] ?? '';

  switch (name) {
    case 'fullName': {
      if (!value.trim()) {
        return 'Full name is required.';
      }
      if (value.trim().length < 2) {
        return 'Full name must be at least 2 characters.';
      }
      return '';
    }
    case 'phone': {
      const cleaned = value.trim();
      if (!cleaned) {
        return 'Phone number is required.';
      }
      if (!DIGITS_ONLY_REGEX.test(cleaned)) {
        return 'Phone number must contain digits only (0-9).';
      }
      if (cleaned.length < 10 || cleaned.length > 15) {
        return 'Phone number must be between 10 and 15 digits.';
      }
      return '';
    }
    case 'email': {
      const cleaned = value.trim();
      if (!cleaned) {
        return 'Email address is required.';
      }
      if (!EMAIL_REGEX.test(cleaned)) {
        return 'Enter a valid email address (for example, name@example.com).';
      }
      return '';
    }
    case 'password': {
      if (!value) {
        return 'Password is required.';
      }
      if (value.length < 8) {
        return 'Password must be at least 8 characters.';
      }
      if (!HAS_LETTER_REGEX.test(value) || !HAS_NUMBER_REGEX.test(value)) {
        return 'Password must include at least one letter and one number.';
      }
      return '';
    }
    case 'confirmPassword': {
      if (!value) {
        return 'Please confirm your password.';
      }
      if (value !== values.password) {
        return 'Passwords do not match.';
      }
      return '';
    }
    default:
      return '';
  }
}

export function validateRegisterForm(values) {
  const fields = ['fullName', 'phone', 'email', 'password', 'confirmPassword'];
  const errors = {};

  for (const field of fields) {
    const message = validateRegisterField(field, values);
    if (message) {
      errors[field] = message;
    }
  }

  return errors;
}
