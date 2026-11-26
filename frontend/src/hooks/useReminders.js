import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  fetchNotificationPreferences,
  fetchUpcomingReminders,
} from '../api/client';
import { useAuth } from '../context/AuthContext';
import { formatTime12h } from '../utils/taskCategories';

export const REMINDERS_UPDATED_EVENT = 'pathpilot:reminders-updated';

export function notifyRemindersChanged() {
  window.dispatchEvent(new Event(REMINDERS_UPDATED_EVENT));
}

function isNowInQuietHours(quietStart, quietEnd, timeZone) {
  if (!quietStart || !quietEnd || quietStart === quietEnd) return false;
  try {
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone: timeZone || 'Asia/Kolkata',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).formatToParts(new Date());
    const hh = parts.find((p) => p.type === 'hour')?.value || '00';
    const mm = parts.find((p) => p.type === 'minute')?.value || '00';
    const currentHm = `${hh}:${mm}`;
    const startHm = quietStart.slice(0, 5);
    const endHm = quietEnd.slice(0, 5);
    if (startHm < endHm) {
      return currentHm >= startHm && currentHm < endHm;
    }
    return currentHm >= startHm || currentHm < endHm;
  } catch {
    return false;
  }
}

export function useReminders() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [upcomingReminders, setUpcomingReminders] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [activeToast, setActiveToast] = useState({ message: '', type: 'info' });

  const timersRef = useRef([]);
  const firedKeysRef = useRef(new Set());
  const storageKey = user?.id ? `pathpilot_fired_reminders_${user.id}` : null;

  // Hydrate deduplication keys for this tab session
  useEffect(() => {
    if (!storageKey) {
      firedKeysRef.current = new Set();
      return;
    }
    try {
      const raw = sessionStorage.getItem(storageKey);
      firedKeysRef.current = new Set(raw ? JSON.parse(raw) : []);
    } catch {
      firedKeysRef.current = new Set();
    }
  }, [storageKey]);

  const persistFiredKey = useCallback(
    (key) => {
      firedKeysRef.current.add(key);
      if (storageKey) {
        try {
          sessionStorage.setItem(storageKey, JSON.stringify([...firedKeysRef.current]));
        } catch {
          // Ignore storage quota issues
        }
      }
    },
    [storageKey]
  );

  const clearScheduledTimers = useCallback(() => {
    for (const id of timersRef.current) {
      window.clearTimeout(id);
    }
    timersRef.current = [];
  }, []);

  const fireReminder = useCallback(
    (item, prefs, isMissed) => {
      const dedupeKey = `${item.taskId}:${item.reminderAtUtc}`;
      if (firedKeysRef.current.has(dedupeKey)) return;

      const quietNow =
        item.inQuietHours ||
        isNowInQuietHours(prefs?.quietHoursStart, prefs?.quietHoursEnd, user?.timeZone);
      if (quietNow) return;

      persistFiredKey(dedupeKey);

      const timeLabel = item.startTime ? formatTime12h(item.startTime) : 'scheduled';
      const prefix = isMissed ? 'You missed: ' : 'Reminder: ';
      const message = `${prefix}${item.title} (${timeLabel})`;

      setActiveToast({
        message,
        type: isMissed ? 'warning' : 'info',
      });

      setAlerts((prev) => [
        {
          id: dedupeKey,
          taskId: item.taskId,
          title: item.title,
          taskDate: item.taskDate,
          timeLabel,
          isMissed,
          read: false,
          firedAt: new Date().toISOString(),
        },
        ...prev.filter((a) => a.id !== dedupeKey),
      ]);

      if (
        prefs?.browserEnabled &&
        typeof window !== 'undefined' &&
        'Notification' in window &&
        window.Notification.permission === 'granted'
      ) {
        try {
          const notif = new window.Notification(
            isMissed ? `Missed Task: ${item.title}` : `PathPilot Reminder`,
            {
              body: `${item.title} at ${timeLabel}`,
              tag: dedupeKey,
            }
          );
          notif.onclick = () => {
            window.focus();
            navigate(`/app/planner/daily?date=${item.taskDate}`);
            notif.close();
          };
        } catch {
          // Ignore environments blocking Notification constructor
        }
      }
    },
    [navigate, persistFiredKey, user?.timeZone]
  );

  const refreshReminders = useCallback(async () => {
    if (!user?.id) {
      clearScheduledTimers();
      setUpcomingReminders([]);
      return;
    }

    try {
      const [items, prefs] = await Promise.all([
        fetchUpcomingReminders(24),
        fetchNotificationPreferences(),
      ]);

      const list = items || [];
      setUpcomingReminders(list);
      clearScheduledTimers();

      const nowMs = Date.now();
      for (const item of list) {
        if (!item.reminderAtUtc) continue;
        const dedupeKey = `${item.taskId}:${item.reminderAtUtc}`;
        if (firedKeysRef.current.has(dedupeKey)) continue;

        const targetMs = new Date(item.reminderAtUtc).getTime();
        const delayMs = targetMs - nowMs;

        if (delayMs <= 0) {
          // Missed by > 60s (e.g. tab sleep/clock jump) => show "You missed: ..." once
          const isMissed = delayMs < -60_000;
          fireReminder(item, prefs, isMissed);
        } else if (delayMs <= 24 * 60 * 60 * 1000) {
          const timerId = window.setTimeout(() => {
            fireReminder(item, prefs, false);
          }, delayMs);
          timersRef.current.push(timerId);
        }
      }
    } catch {
      // Ignore transient polling failures
    }
  }, [user?.id, clearScheduledTimers, fireReminder]);

  useEffect(() => {
    refreshReminders();

    const intervalId = window.setInterval(refreshReminders, 60_000);
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') {
        refreshReminders();
      }
    };
    const handleUpdatedEvent = () => {
      refreshReminders();
    };

    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener(REMINDERS_UPDATED_EVENT, handleUpdatedEvent);

    return () => {
      clearScheduledTimers();
      window.clearInterval(intervalId);
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener(REMINDERS_UPDATED_EVENT, handleUpdatedEvent);
    };
  }, [refreshReminders, clearScheduledTimers]);

  const markAllRead = useCallback(() => {
    setAlerts((prev) => prev.map((a) => ({ ...a, read: true })));
  }, []);

  const dismissToast = useCallback(() => {
    setActiveToast({ message: '', type: 'info' });
  }, []);

  const unreadCount = alerts.filter((a) => !a.read).length;

  return {
    upcomingReminders,
    alerts,
    unreadCount,
    activeToast,
    dismissToast,
    markAllRead,
    refreshReminders,
  };
}
