// TV browser presentation only: move existing controls, never replace their actions or scope gates.
function shelbyLayout(makeKey) {
  const rc = document.querySelector('.rc');
  if (!rc) return;
  const top = el('div', { className: 'shelby-mainkeys' });
  top.append(rc.querySelector('[data-k="POWER"]'), $('#r-home'), $('#r-back'));
  for (const entry of (REMOTE.rows.shelby || []).flat().filter(remoteHere)) top.append(makeKey(entry));
  rc.prepend(top);
  const utilities = el('details', { className: 'shelby-utilities' },
    el('summary', { textContent: T('remote.more_keys') }));
  utilities.dataset.needs = 'control';
  utilities.append(rc.querySelector('.rrow.four'), $('#more'), rc.querySelector('[data-rows="nav"]'), rc.querySelector('.mid'));
  document.querySelector('.remote-grid').append(utilities);
  const tabs = document.querySelector('nav.tabs');
  tabs.querySelector('[data-tab="remote"]').after($('#kbdbtn'));
  const tools = document.querySelector('.shelby-headtools');
  tools.append($('#suptv'), $('#aboutbtn'));
  $('#aboutbtn').dataset.asks = ''; // Opens the existing settings dialog; no extra icon-only confirmation.
  const quick = $('#quick').parentElement;
  quick.classList.add('shelby-apps');
  const shortcuts = el('div', { className: 'shelby-shortcuts' }, $('#quick'), $('#r-apps'));
  const search = el('div', { className: 'shelby-search' }, UI.icon('search'), $('#quickq'), $('#voice'));
  quick.append(shortcuts, search);
  // Keep the familiar remote tab icon distinct from the apps tab without adding a new action.
  const icon = tabs.querySelector('[data-tab="remote"] use');
  if (icon) icon.setAttribute('href', '#i-tv');
}
