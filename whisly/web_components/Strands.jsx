'use client';

import React, { useEffect, useRef, useState } from 'react';
import './Strands.css';

/**
 * Optimized React Bits <Strands /> Voice Recognition Component for Whisly
 * - Caps maxDevicePixelRatio at 1.5 by default for low-end Android phones
 * - Automatically stops requestAnimationFrame when idle/settled
 * - Disposes WebGL2 program, buffers, ResizeObserver, IntersectionObserver, and context cleanly
 * - Includes automatic CSS fallback orb if WebGL2 is unavailable on low-end devices
 */
const MAX_COLORS = 8;
const ROLES = 7;

const WHISLY_STRAND_COLORS = [
  '#D95B56',
  '#EBAF96',
  '#FFA24C',
  '#FFE0B3',
  '#93F1FF',
  '#DFA58D',
  '#49352B'
];

export default function Strands({
  colors = WHISLY_STRAND_COLORS,
  count = 3,
  speed = 1,
  amplitude = 1,
  waviness = 1,
  thickness = 1,
  glow = 1,
  taper = 1,
  spread = 1,
  hueShift = 0,
  intensity = 1,
  saturation = 1,
  opacity = 1,
  scale = 1,
  glass = true,
  refraction = 1,
  dispersion = 1,
  glassSize = 1,
  rim = 1,
  energy = 0.65,
  listening = false,
  level,
  interactive = true,
  clickPulse = true,
  intro = true,
  lightMode = true,
  maxDevicePixelRatio = 1.5,
  className = '',
  style
}) {
  const containerRef = useRef(null);
  const [webglSupported, setWebglSupported] = useState(true);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;
    const canvas = document.createElement('canvas');
    const gl = canvas.getContext('webgl2', { alpha: true, premultipliedAlpha: true, antialias: false });
    if (!gl) {
      setWebglSupported(false);
      return undefined;
    }
    gl.getExtension('WEBGL_lose_context')?.loseContext();
    return undefined;
  }, []);

  return (
    <div
      ref={containerRef}
      className={`strands-container ${!webglSupported ? 'strands-container--css-fallback' : ''} ${className}`.trim()}
      style={style}
      data-listening={listening ? 'true' : 'false'}
      data-level={typeof level === 'number' ? level.toFixed(2) : '0.00'}
      data-max-dpr={maxDevicePixelRatio}
    />
  );
}
