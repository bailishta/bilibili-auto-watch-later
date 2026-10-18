// 保留原有 select 的值与 change 事件；自定义菜单负责展示和键盘操作。
export function initSortPicker(select) {
  const picker = document.getElementById('sort-picker');
  const trigger = document.getElementById('sort-trigger');
  const label = document.getElementById('sort-label');
  const menu = document.getElementById('sort-options');
  const options = Array.from(select.options);
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let opened = false;
  let activeIndex = 0;
  let animation;
  let revision = 0;

  const items = options.map((option, index) => {
    const item = document.createElement('div');
    item.id = `sort-option-${option.value}`;
    item.className = 'sort-option';
    item.setAttribute('role', 'option');
    item.textContent = option.textContent;
    item.addEventListener('pointerdown', event => event.preventDefault());
    item.addEventListener('click', () => choose(index));
    menu.append(item);
    return item;
  });

  function syncSelection() {
    const selectedIndex = Math.max(0, select.selectedIndex);
    label.textContent = options[selectedIndex].textContent;
    items.forEach((item, index) => item.setAttribute('aria-selected', String(index === selectedIndex)));
  }

  function activate(index) {
    activeIndex = index;
    items.forEach((item, itemIndex) => item.classList.toggle('is-active', itemIndex === index));
    trigger.setAttribute('aria-activedescendant', items[index].id);
    items[index].scrollIntoView({ block: 'nearest' });
  }

  function open(index = Math.max(0, select.selectedIndex)) {
    ++revision;
    animation?.cancel();
    opened = true;
    menu.hidden = false;
    menu.inert = false;
    picker.classList.add('is-open');
    trigger.setAttribute('aria-expanded', 'true');
    picker.classList.remove('opens-up');
    const rect = trigger.getBoundingClientRect();
    if (innerHeight - rect.bottom < menu.offsetHeight + 8 && rect.top > innerHeight - rect.bottom) picker.classList.add('opens-up');
    activate(index);
    if (!reducedMotion.matches) animation = menu.animate([
      { opacity: 0, transform: `translateY(${picker.classList.contains('opens-up') ? '6px' : '-6px'}) scale(.96)` },
      { opacity: 1, transform: 'translateY(0) scale(1)' }
    ], { duration: 220, easing: 'cubic-bezier(.16, 1, .3, 1)' });
  }

  function close(restoreFocus = false) {
    if (!opened) return;
    const currentRevision = ++revision;
    animation?.cancel();
    opened = false;
    menu.inert = true;
    picker.classList.remove('is-open');
    trigger.setAttribute('aria-expanded', 'false');
    trigger.removeAttribute('aria-activedescendant');
    if (restoreFocus) trigger.focus({ preventScroll: true });
    if (reducedMotion.matches) menu.hidden = true;
    else {
      animation = menu.animate([
        { opacity: 1, transform: 'translateY(0) scale(1)' },
        { opacity: 0, transform: `translateY(${picker.classList.contains('opens-up') ? '4px' : '-4px'}) scale(.98)` }
      ], { duration: 140, easing: 'cubic-bezier(.4, 0, 1, 1)', fill: 'forwards' });
      animation.onfinish = () => { if (currentRevision === revision) menu.hidden = true; };
    }
  }

  function choose(index) {
    if (!opened) return;
    select.value = options[index].value;
    select.dispatchEvent(new Event('change', { bubbles: true }));
    close(true);
  }

  trigger.addEventListener('click', () => opened ? close() : open());
  trigger.addEventListener('keydown', event => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!opened) open();
      else activate((activeIndex + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length);
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      const index = event.key === 'Home' ? 0 : items.length - 1;
      if (!opened) open(index); else activate(index);
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      if (opened) choose(activeIndex); else open();
    } else if (event.key === 'Escape' && opened) {
      event.preventDefault(); close(true);
    } else if (event.key === 'Tab') close();
  });
  document.addEventListener('pointerdown', event => { if (!picker.contains(event.target)) close(); });
  picker.addEventListener('focusout', event => { if (!picker.contains(event.relatedTarget)) close(); });
  window.addEventListener('resize', () => close());
  select.addEventListener('change', syncSelection);
  syncSelection();
}
