// Preferencia de movimiento reducido
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// Reveal al hacer scroll
if (!reduceMotion) {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('reveal');
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.12 }
  );

  document.querySelectorAll('.bullet-card, .profile-card').forEach((el) => observer.observe(el));
}

// Tilt en las tarjetas de perfil
document.querySelectorAll('.profile-card').forEach((card) => {
  const MAX_X = 6; // grados
  const MAX_Y = 8; // grados

  function reset() {
    card.style.transform = 'translateZ(0)';
  }

  card.addEventListener('mousemove', (e) => {
    const r = card.getBoundingClientRect();
    const x = e.clientX - r.left;
    const y = e.clientY - r.top;
    const dx = (x - r.width / 2) / (r.width / 2);
    const dy = (y - r.height / 2) / (r.height / 2);
    const rotX = (-dy * MAX_X).toFixed(2);
    const rotY = (dx * MAX_Y).toFixed(2);
    card.style.transform = `rotateX(${rotX}deg) rotateY(${rotY}deg)`;
  });

  card.addEventListener('mouseleave', reset);
  card.addEventListener('blur', reset);
});

// Efecto spotlight en el panel de vidrio
const panel = document.getElementById('panel');
if (panel) {
  const setSpot = (x, y, rect) => {
    const px = ((x - rect.left) / rect.width) * 100;
    const py = ((y - rect.top) / rect.height) * 100;
    panel.style.setProperty('--gx', `${px}%`);
    panel.style.setProperty('--gy', `${py}%`);
  };

  panel.addEventListener('mousemove', (e) => {
    const rect = panel.getBoundingClientRect();
    setSpot(e.clientX, e.clientY, rect);
  });

  panel.addEventListener('mouseleave', () => {
    panel.style.setProperty('--gx', '50%');
    panel.style.setProperty('--gy', '50%');
  });
}

// Interacción sencilla del botón Blog
const blogBtn = document.querySelector('.nav-button');
if (blogBtn) {
  blogBtn.addEventListener('click', () => {
    alert('Aquí podría abrirse un menú del Blog.');
  });
}

// ----------------------
// Tema claro/oscuro
// ----------------------
const THEME_KEY = 'theme-preference';

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  const toggleBtn = document.getElementById('theme-toggle');
  if (toggleBtn) {
    toggleBtn.setAttribute('aria-checked', theme === 'dark' ? 'true' : 'false');
    toggleBtn.innerHTML = `<span aria-hidden="true">${theme === 'dark' ? '☀️' : '🌙'}</span>`;
  }
  if (typeof window.refreshCanvasColors === 'function') {
    window.refreshCanvasColors();
  }
}

function initTheme() {
  let saved = localStorage.getItem(THEME_KEY);
  if (!saved) {
    saved = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  applyTheme(saved);
}

initTheme();

const toggle = document.getElementById('theme-toggle');
if (toggle) {
  toggle.addEventListener('click', () => {
    const current = document.documentElement.getAttribute('data-theme') || 'light';
    const next = current === 'dark' ? 'light' : 'dark';
    localStorage.setItem(THEME_KEY, next);
    applyTheme(next);
  });
}

// Cambios del sistema si no hay preferencia guardada
const mqDark = window.matchMedia('(prefers-color-scheme: dark)');
try {
  mqDark.addEventListener('change', (e) => {
    if (!localStorage.getItem(THEME_KEY)) {
      applyTheme(e.matches ? 'dark' : 'light');
    }
  });
} catch (_) {
  // Safari
  mqDark.addListener((e) => {
    if (!localStorage.getItem(THEME_KEY)) {
      applyTheme(e.matches ? 'dark' : 'light');
    }
  });
}

// ----------------------
// Fondo dinámico de líneas curvas
// ----------------------
function initBackground() {
  const canvas = document.getElementById('bg-lines');
  if (!canvas) return;
  const dpr = Math.max(1, window.devicePixelRatio || 1);
  const ctx = canvas.getContext('2d');
  let w, h;
  const LINES = 10; // menos líneas para suavizar
  const lines = [];

  function viewportSize() {
    if (window.visualViewport) {
      return { w: Math.ceil(window.visualViewport.width), h: Math.ceil(window.visualViewport.height) };
    }
    return { w: Math.ceil(window.innerWidth), h: Math.ceil(window.innerHeight) };
  }

  function resize() {
    const vp = viewportSize();
    w = canvas.width = Math.floor(vp.w * dpr);
    h = canvas.height = Math.floor(vp.h * dpr);
    canvas.style.width = '130vw';
    canvas.style.height = '130vh';
  }
  resize();
  window.addEventListener('resize', resize);

  for (let i = 0; i < LINES; i++) {
    lines.push({
      base: (h / (LINES + 1)) * (i + 1),
      amp: h * 0.032 * (0.7 + Math.random() * 0.5), // menor amplitud
      freq: 0.0008 + Math.random() * 0.0006,        // menor frecuencia
      phase: Math.random() * Math.PI * 2,
      speed: 0.004 + Math.random() * 0.003,         // menor velocidad
    });
  }

  function getColors() {
    const cs = getComputedStyle(document.documentElement);
    return [cs.getPropertyValue('--line1').trim(), cs.getPropertyValue('--line2').trim()];
  }

  function draw() {
    ctx.clearRect(0, 0, w, h);
    const [c1, c2] = getColors();
    const grad = ctx.createLinearGradient(0, 0, w, 0);
    grad.addColorStop(0, c1);
    grad.addColorStop(1, c2);
    ctx.lineWidth = 1.2 * dpr; // trazos más finos
    const dark = (document.documentElement.getAttribute('data-theme') === 'dark');
    ctx.globalAlpha = dark ? 0.30 : 0.42; // menos opacidad
    for (const L of lines) {
      ctx.beginPath();
      for (let x = 0; x <= w; x += 10 * dpr) { // paso mayor
        const y = L.base + Math.sin(x * L.freq + L.phase) * L.amp + Math.sin(x * L.freq * 0.5 + L.phase * 1.7) * L.amp * 0.28;
        if (x === 0) ctx.moveTo(x / dpr, y / dpr);
        else ctx.lineTo(x / dpr, y / dpr);
      }
      ctx.strokeStyle = grad;
      ctx.stroke();
      L.phase += L.speed * (dark ? 0.7 : 0.9); // más lento
    }
    if (!reduceMotion) requestAnimationFrame(draw);
  }

  if (reduceMotion) draw();
  else requestAnimationFrame(draw);

  // Expone función para actualizar colores al cambiar de tema
  window.refreshCanvasColors = () => {
    // simplemente fuerza un repaint limpiando y redibujando una vez
    draw();
  };
}

initBackground();