import React, { useEffect, useRef, useState, useCallback, memo } from 'react';
import './GooeyNav.css';

/**
 * Optimized React Bits GooeyNav for Whisly
 * - Particle count limited to 6 (5-7 range for low-end Android WebViews/browsers)
 * - Animation duration capped at 380ms (350-450ms range)
 * - Respects prefers-reduced-motion and pauses timers when settled
 * - Connects directly to application route callbacks (Home, Translate, History, Profile)
 */
const DEFAULT_ITEMS = [
  { id: 'home', label: 'Home', route: '/home' },
  { id: 'translate', label: 'Translate', route: '/translate' },
  { id: 'history', label: 'History', route: '/history' },
  { id: 'profile', label: 'Profile', route: '/profile' }
];

const PARTICLE_COUNT = 6;
const ANIMATION_DURATION_MS = 380;

const GooeyNav = memo(function GooeyNav({
  items = DEFAULT_ITEMS,
  activeRoute = '/translate',
  onNavigate,
  darkMode = false,
  lowPerformanceMode = false,
  className = ''
}) {
  const navRef = useRef(null);
  const pillRef = useRef(null);
  const particlesRef = useRef(null);
  const cleanupTimerRef = useRef(null);
  const isInitialMountRef = useRef(true);

  const activeIndex = Math.max(
    0,
    items.findIndex(item => item.route === activeRoute || item.id === activeRoute)
  );
  const [currentIndex, setCurrentIndex] = useState(activeIndex);

  const prefersReducedMotion = useCallback(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }, []);

  const clearParticles = useCallback(() => {
    if (cleanupTimerRef.current) {
      clearTimeout(cleanupTimerRef.current);
      cleanupTimerRef.current = null;
    }
    if (particlesRef.current) {
      while (particlesRef.current.firstChild) {
        particlesRef.current.removeChild(particlesRef.current.firstChild);
      }
    }
  }, []);

  const spawnOrganicParticles = useCallback(
    targetRect => {
      if (lowPerformanceMode || prefersReducedMotion() || !particlesRef.current || !navRef.current) {
        return;
      }
      clearParticles();
      const navRect = navRef.current.getBoundingClientRect();
      const centerX = targetRect.left - navRect.left + targetRect.width / 2;
      const centerY = targetRect.top - navRect.top + targetRect.height / 2;

      const fragment = document.createDocumentFragment();
      for (let i = 0; i < PARTICLE_COUNT; i++) {
        const angle = (Math.PI * 2 * i) / PARTICLE_COUNT;
        const distance = 14 + (i % 2) * 8;
        const dx = Math.cos(angle) * distance;
        const dy = Math.sin(angle) * (distance * 0.55);
        const size = 10 + (i % 3) * 3;

        const span = document.createElement('span');
        span.className = 'whisly-gooey-particle';
        span.style.width = `${size}px`;
        span.style.height = `${size}px`;
        span.style.left = `${centerX - size / 2}px`;
        span.style.top = `${centerY - size / 2}px`;
        span.style.setProperty('--tx', `${dx.toFixed(1)}px`);
        span.style.setProperty('--ty', `${dy.toFixed(1)}px`);
        fragment.appendChild(span);
      }
      particlesRef.current.appendChild(fragment);

      cleanupTimerRef.current = setTimeout(() => {
        clearParticles();
      }, ANIMATION_DURATION_MS);
    },
    [clearParticles, lowPerformanceMode, prefersReducedMotion]
  );

  const syncPillPosition = useCallback(
    (index, animate) => {
      const navEl = navRef.current;
      const pillEl = pillRef.current;
      if (!navEl || !pillEl) return;

      const buttons = navEl.querySelectorAll('.whisly-gooey-nav__item');
      const targetBtn = buttons[index];
      if (!targetBtn) return;

      const navRect = navEl.getBoundingClientRect();
      const btnRect = targetBtn.getBoundingClientRect();
      const offsetLeft = btnRect.left - navRect.left;

      pillEl.style.transition =
        animate && !prefersReducedMotion()
          ? `transform ${ANIMATION_DURATION_MS}ms cubic-bezier(0.22, 1, 0.36, 1), width ${ANIMATION_DURATION_MS}ms cubic-bezier(0.22, 1, 0.36, 1)`
          : 'none';
      pillEl.style.width = `${btnRect.width}px`;
      pillEl.style.transform = `translate3d(${offsetLeft}px, 0, 0)`;

      if (animate) {
        spawnOrganicParticles(btnRect);
      }
    },
    [prefersReducedMotion, spawnOrganicParticles]
  );

  useEffect(() => {
    setCurrentIndex(activeIndex);
    const shouldAnimate = !isInitialMountRef.current;
    isInitialMountRef.current = false;
    syncPillPosition(activeIndex, shouldAnimate);
  }, [activeIndex, syncPillPosition]);

  useEffect(() => {
    const navEl = navRef.current;
    if (!navEl || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(() => {
      syncPillPosition(currentIndex, false);
    });
    observer.observe(navEl);
    return () => {
      observer.disconnect();
      clearParticles();
    };
  }, [currentIndex, syncPillPosition, clearParticles]);

  const handleSelect = (item, idx) => {
    if (idx === currentIndex) return;
    setCurrentIndex(idx);
    syncPillPosition(idx, true);
    if (typeof onNavigate === 'function') {
      onNavigate(item.route || item.id);
    }
  };

  return (
    <nav
      ref={navRef}
      aria-label="Primary Navigation"
      className={`whisly-gooey-nav ${darkMode ? 'whisly-gooey-nav--dark' : 'whisly-gooey-nav--light'} ${className}`.trim()}
    >
      <div className="whisly-gooey-nav__liquid-layer" aria-hidden="true">
        <div ref={pillRef} className="whisly-gooey-nav__pill" />
        <div ref={particlesRef} className="whisly-gooey-nav__particles" />
      </div>

      <ul className="whisly-gooey-nav__list" role="tablist">
        {items.map((item, idx) => {
          const isSelected = idx === currentIndex;
          return (
            <li key={item.id || item.route} className="whisly-gooey-nav__slot" role="presentation">
              <button
                type="button"
                role="tab"
                aria-selected={isSelected}
                className={`whisly-gooey-nav__item ${isSelected ? 'is-active' : ''}`}
                onClick={() => handleSelect(item, idx)}
              >
                {item.icon ? <span className="whisly-gooey-nav__icon">{item.icon}</span> : null}
                <span className="whisly-gooey-nav__label">{item.label}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </nav>
  );
});

export default GooeyNav;
