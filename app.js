// Ironwood Document Services PWA
// This script handles view navigation, checklist persistence and
// service‑worker registration.

document.addEventListener('DOMContentLoaded', () => {
  // Register the service worker for offline capability
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./service-worker.js').catch((err) => {
      console.error('ServiceWorker registration failed:', err);
    });
  }

  const views = document.querySelectorAll('.view');
  const homeSection = document.getElementById('home');
  const cards = document.querySelectorAll('.card');
  const backButtons = document.querySelectorAll('.back-btn');

  // Navigate to a section
  function showView(id) {
    views.forEach((view) => {
      if (view.id === id) {
        view.classList.add('active-view');
      } else {
        view.classList.remove('active-view');
      }
    });
    window.scrollTo(0, 0);
  }

  // Click handlers for service cards
  cards.forEach((card) => {
    card.addEventListener('click', () => {
      const target = card.getAttribute('data-target');
      if (target) {
        showView(target);
      }
    });
  });

  // Click handlers for back buttons
  backButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      showView('home');
    });
  });

  // Persistence functions
  function loadChecklist(service) {
    try {
      const data = JSON.parse(localStorage.getItem(`ironwood_checklist_${service}`));
      return Array.isArray(data) ? data : [];
    } catch (e) {
      return [];
    }
  }

  function saveChecklist(service, values) {
    localStorage.setItem(`ironwood_checklist_${service}`, JSON.stringify(values));
  }

  // Initialise each checklist from localStorage
  const checklists = document.querySelectorAll('.checklist');
  checklists.forEach((list) => {
    const service = list.getAttribute('data-service');
    const storedValues = loadChecklist(service);
    const checkboxes = list.querySelectorAll('input[type="checkbox"]');
    checkboxes.forEach((checkbox, idx) => {
      // Load stored value
      if (storedValues[idx]) {
        checkbox.checked = true;
      }
      // Add change listener
      checkbox.addEventListener('change', () => {
        const values = Array.from(checkboxes).map((cb) => cb.checked);
        saveChecklist(service, values);
      });
    });
  });
});