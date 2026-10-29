/* DeliverEase frontend - plain HTML/CSS/JavaScript talking to the Spring Boot REST API */
const API = '/api';
let session = JSON.parse(localStorage.getItem('de_session') || 'null');

const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmt = d => d ? new Date(d).toLocaleString() : '';
const money = n => 'Rs. ' + Number(n).toFixed(2);
const badge = (s, label) => `<span class="badge ${s}">${label || s.replace(/_/g, ' ')}</span>`;

function toast(msg, isError) {
  const t = $('#toast');
  t.textContent = msg; t.className = 'toast' + (isError ? ' error' : '');
  setTimeout(() => t.classList.add('hidden'), 3500);
}

async function api(path, method = 'GET', body) {
  const headers = { 'Content-Type': 'application/json' };
  if (session) headers.Authorization = 'Bearer ' + session.token;
  const res = await fetch(API + path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let data = null;
  try { data = await res.json(); } catch (e) { /* empty body */ }
  if (res.status === 401 && session && !path.startsWith('/auth')) {
    logout(); throw new Error('Session expired. Please log in again.');
  }
  if (res.status === 403) throw new Error('You are not allowed to do this.');
  if (!res.ok) throw new Error((data && data.message) || 'Request failed (' + res.status + ')');
  return data;
}
const run = async fn => { try { await fn(); } catch (e) { toast(e.message, true); } };

/* ---------------------------------------------------------- authentication */
function toggleAuth(showRegister) {
  $('#login-form').classList.toggle('hidden', showRegister);
  $('#register-form').classList.toggle('hidden', !showRegister);
  $('#auth-title').textContent = showRegister ? 'Customer Registration' : 'Login';
}
function saveSession(r) { session = r; localStorage.setItem('de_session', JSON.stringify(r)); start(); }
const login = () => run(async () => saveSession(await api('/auth/login', 'POST',
  { email: $('#l-email').value, password: $('#l-pass').value })));
const register = () => run(async () => saveSession(await api('/auth/register', 'POST', {
  name: $('#r-name').value, email: $('#r-email').value, password: $('#r-pass').value,
  phone: $('#r-phone').value, address: $('#r-address').value })));
function logout() { session = null; localStorage.removeItem('de_session'); start(); }

/* -------------------------------------------------------------- navigation */
const TABS = {
  CUSTOMER: [['place', 'Place Order'], ['orders', 'My Orders'], ['track', 'Track'], ['profile', 'Profile']],
  ADMIN: [['dashboard', 'Dashboard'], ['orders', 'Orders'], ['partners', 'Partners'], ['track', 'Track']],
  DELIVERY_PARTNER: [['deliveries', 'My Deliveries'], ['track', 'Track']]
};
function start() {
  const loggedIn = !!session;
  $('#auth-view').classList.toggle('hidden', loggedIn);
  $('#app-view').classList.toggle('hidden', !loggedIn);
  $('#nav').innerHTML = ''; $('#who').innerHTML = '';
  if (!loggedIn) return;
  $('#who').innerHTML = `${esc(session.name)} (${session.role.replace('_', ' ')}) <button onclick="logout()">Logout</button>`;
  $('#nav').innerHTML = TABS[session.role].map(([id, label]) =>
    `<button id="tab-${id}" onclick="show('${id}')">${label}</button>`).join('');
  show(TABS[session.role][0][0]);
}
function show(tab) {
  document.querySelectorAll('nav button').forEach(b => b.classList.toggle('active', b.id === 'tab-' + tab));
  run(() => views[tab]());
}
const content = html => { $('#content').innerHTML = html; };

/* ------------------------------------------------------------------ views */
const views = {
  /* ---- customer ---- */
  async place() {
    content(`<div class="card"><h2>Place a new order</h2>
      <div class="row">
        <div><label>Pickup address</label><input id="o-pick"></div>
        <div><label>Drop-off address</label><input id="o-drop"></div>
        <div><label>Package details</label><input id="o-pack" placeholder="e.g. Documents, small box"></div>
        <div><label>Weight (kg)</label><input id="o-wt" type="number" min="0.1" step="0.1" value="1" oninput="estimate()"></div>
        <div><label>Delivery type</label><select id="o-type" onchange="estimate()">
          <option value="STANDARD">Standard (Rs. 40 + Rs. 10/kg)</option>
          <option value="EXPRESS">Express (Rs. 80 + Rs. 10/kg)</option>
          <option value="SAME_DAY">Same day (Rs. 120 + Rs. 10/kg)</option></select></div>
      </div>
      <p>Estimated delivery charge: <b id="o-est"></b></p>
      <button onclick="placeOrder()">Place Order</button>
      <div id="o-result"></div></div>`);
    estimate();
  },
  async orders() {
    const isAdmin = session.role === 'ADMIN';
    const filter = isAdmin ? ($('#f-status') ? $('#f-status').value : '') : '';
    const list = await api('/orders' + (filter ? '?status=' + filter : ''));
    const free = isAdmin ? (await api('/partners')).filter(p => p.active && p.available) : [];
    const rows = list.map(o => `<tr>
      <td><b>${esc(o.trackingId)}</b><br><span class="muted small">${fmt(o.createdAt)}</span></td>
      ${isAdmin ? `<td>${esc(o.customerName)}</td>` : ''}
      <td>${esc(o.pickupAddress)} &rarr; ${esc(o.dropAddress)}<br><span class="muted small">${esc(o.packageDetails)} &middot; ${o.weightKg} kg</span></td>
      <td>${o.deliveryType.replace('_', ' ')}</td><td>${money(o.charge)}</td>
      <td>${badge(o.status)}</td><td>${esc(o.partnerName || '-')}</td>
      ${isAdmin ? '' : `<td>${['DELIVERED', 'CANCELLED'].includes(o.status) ? '-' : `<span class="pin">${esc(o.deliveryPin)}</span>`}</td>`}
      <td>${orderActions(o, isAdmin, free)}</td></tr>`).join('');
    content(`<div class="card"><h2>${isAdmin ? 'All orders' : 'My orders'}</h2>
      ${isAdmin ? `<label>Filter by status</label><select id="f-status" onchange="show('orders')">
        <option value="">All</option>${['PLACED', 'ASSIGNED', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED']
          .map(s => `<option ${s === filter ? 'selected' : ''}>${s}</option>`).join('')}</select>` : ''}
      <div class="table-wrap"><table><tr><th>Tracking ID</th>${isAdmin ? '<th>Customer</th>' : ''}<th>Route</th><th>Type</th>
      <th>Charge</th><th>Status</th><th>Partner</th>${isAdmin ? '' : '<th>Delivery PIN</th>'}<th>Action</th></tr>
      ${rows || '<tr><td colspan="9" class="muted">No orders found.</td></tr>'}</table></div></div>`);
  },
  async profile() {
    const p = await api('/profile');
    content(`<div class="card"><h2>My profile</h2><div class="row">
      <div><label>Name</label><input id="p-name" value="${esc(p.name)}"></div>
      <div><label>Email</label><input value="${esc(p.email)}" disabled></div>
      <div><label>Phone</label><input id="p-phone" value="${esc(p.phone)}"></div>
      <div><label>Address</label><input id="p-address" value="${esc(p.address)}"></div></div>
      <button onclick="saveProfile()">Save changes</button></div>`);
  },
  track() {
    content(`<div class="card"><h2>Track an order</h2>
      <input id="t-id" placeholder="Enter tracking ID"><button onclick="track('t-id','t-out')">Track</button>
      <div id="t-out"></div></div>`);
  },
  /* ---- admin ---- */
  async dashboard() {
    const d = await api('/dashboard');
    const s = (label, v) => `<div class="stat"><b>${v}</b><span>${label}</span></div>`;
    content(`<h2>Dashboard</h2><div class="stats">
      ${s('Total orders', d.totalOrders)}${s('Placed (waiting)', d.PLACED)}${s('Assigned', d.ASSIGNED)}
      ${s('Picked up', d.PICKED_UP)}${s('Out for delivery', d.OUT_FOR_DELIVERY)}${s('Delivered', d.DELIVERED)}
      ${s('Cancelled', d.CANCELLED)}${s('Customers', d.totalCustomers)}${s('Active partners', d.activePartners)}
      ${s('Available partners', d.availablePartners)}${s('Revenue (delivered)', money(d.revenue))}</div>`);
  },
  async partners() {
    const list = await api('/partners');
    const rows = list.map(p => `<tr><td>${esc(p.name)}</td><td>${esc(p.phone)}</td><td>${esc(p.vehicleType)}</td>
      <td>${esc(p.email)}</td><td>${p.active ? (p.available ? badge('DELIVERED', 'Available') : badge('ASSIGNED', 'Busy')) : badge('CANCELLED', 'Inactive')}</td>
      <td><button class="small ghost" onclick="editPartner(${p.id},'${esc(p.name)}','${esc(p.phone)}','${esc(p.vehicleType)}')">Edit</button>
      <button class="small ${p.active ? 'danger' : 'ok'}" onclick="togglePartner(${p.id},${!p.active})">${p.active ? 'Deactivate' : 'Activate'}</button></td></tr>`).join('');
    content(`<div class="card"><h2>Add delivery partner</h2><div class="row">
      <div><label>Name</label><input id="d-name"></div><div><label>Phone</label><input id="d-phone"></div>
      <div><label>Vehicle type</label><input id="d-veh" placeholder="Bike / Scooter / Van"></div>
      <div><label>Login email</label><input id="d-email" type="email"></div>
      <div><label>Login password</label><input id="d-pass" type="password"></div></div>
      <button onclick="addPartner()">Add Partner</button></div>
      <div class="card"><h2>Delivery partners</h2><div class="table-wrap"><table>
      <tr><th>Name</th><th>Phone</th><th>Vehicle</th><th>Login email</th><th>Status</th><th>Action</th></tr>${rows}</table></div></div>`);
  },
  /* ---- delivery partner ---- */
  async deliveries() {
    const list = await api('/orders');
    const next = { ASSIGNED: ['PICKED_UP', 'Mark picked up'], PICKED_UP: ['OUT_FOR_DELIVERY', 'Out for delivery'] };
    const rows = list.map(o => {
      let act = '-';
      if (next[o.status]) act = `<button class="small" onclick="setStatus(${o.id},'${next[o.status][0]}')">${next[o.status][1]}</button>`;
      if (o.status === 'OUT_FOR_DELIVERY') act = `<input id="pin-${o.id}" placeholder="Customer PIN" style="width:120px">
        <button class="small ok" onclick="confirmDelivery(${o.id})">Confirm delivery</button>`;
      return `<tr><td><b>${esc(o.trackingId)}</b></td><td>${esc(o.pickupAddress)} &rarr; ${esc(o.dropAddress)}<br>
        <span class="muted small">${esc(o.packageDetails)} &middot; ${o.weightKg} kg</span></td><td>${badge(o.status)}</td><td>${act}</td></tr>`;
    }).join('');
    content(`<div class="card"><h2>My deliveries</h2><div class="table-wrap"><table>
      <tr><th>Tracking ID</th><th>Route</th><th>Status</th><th>Action</th></tr>
      ${rows || '<tr><td colspan="4" class="muted">No deliveries assigned yet.</td></tr>'}</table></div></div>`);
  }
};

/* ---------------------------------------------------------------- actions */
function estimate() {
  const base = { STANDARD: 40, EXPRESS: 80, SAME_DAY: 120 }[$('#o-type').value];
  $('#o-est').textContent = money(base + 10 * (parseFloat($('#o-wt').value) || 0));
}
const placeOrder = () => run(async () => {
  const o = await api('/orders', 'POST', { pickupAddress: $('#o-pick').value, dropAddress: $('#o-drop').value,
    packageDetails: $('#o-pack').value, weightKg: parseFloat($('#o-wt').value), deliveryType: $('#o-type').value });
  $('#o-result').innerHTML = `<div class="notice"><b>Order placed!</b><br>Tracking ID: <b>${esc(o.trackingId)}</b><br>
    Delivery PIN: <span class="pin">${esc(o.deliveryPin)}</span> (share only with the delivery partner on arrival)<br>
    Charge: ${money(o.charge)} &middot; Status: ${badge(o.status)} ${o.partnerName ? '&middot; Partner: ' + esc(o.partnerName) : ''}</div>`;
  toast('Order placed successfully');
});
const cancelOrder = id => confirm('Cancel this order?') && run(async () => { await api(`/orders/${id}/cancel`, 'PUT'); toast('Order cancelled'); show('orders'); });
const assignOrder = id => run(async () => {
  const pid = $('#as-' + id).value; if (!pid) return toast('Select a partner first', true);
  await api(`/orders/${id}/assign?partnerId=${pid}`, 'PUT'); toast('Order assigned'); show('orders');
});
const setStatus = (id, status, pin) => run(async () => { await api(`/orders/${id}/status`, 'PUT', { status, pin }); toast('Status updated'); show('deliveries'); });
const confirmDelivery = id => setStatus(id, 'DELIVERED', $('#pin-' + id).value);
const saveProfile = () => run(async () => {
  await api('/profile', 'PUT', { name: $('#p-name').value, phone: $('#p-phone').value, address: $('#p-address').value });
  toast('Profile updated');
});
const addPartner = () => run(async () => {
  await api('/partners', 'POST', { name: $('#d-name').value, phone: $('#d-phone').value, vehicleType: $('#d-veh').value,
    email: $('#d-email').value, password: $('#d-pass').value });
  toast('Delivery partner added'); show('partners');
});
const editPartner = (id, name, phone, vehicleType) => run(async () => {
  const n = prompt('Name', name); if (n === null) return;
  const p = prompt('Phone', phone); if (p === null) return;
  const v = prompt('Vehicle type', vehicleType); if (v === null) return;
  await api('/partners/' + id, 'PUT', { name: n, phone: p, vehicleType: v }); toast('Partner updated'); show('partners');
});
const togglePartner = (id, value) => run(async () => { await api(`/partners/${id}/active?value=${value}`, 'PUT'); show('partners'); });

function orderActions(o, isAdmin, free) {
  const cancel = ['PLACED', 'ASSIGNED'].includes(o.status) ? `<button class="small danger" onclick="cancelOrder(${o.id})">Cancel</button>` : '';
  if (isAdmin && o.status === 'PLACED') {
    return `<select id="as-${o.id}" style="width:130px"><option value="">Partner...</option>${free.map(p =>
      `<option value="${p.id}">${esc(p.name)}</option>`).join('')}</select>
      <button class="small" onclick="assignOrder(${o.id})">Assign</button>${cancel}`;
  }
  return cancel || '-';
}

/* ------------------------------------------------------- public tracking */
const track = (inId, outId) => run(async () => {
  const id = $('#' + inId).value.trim(); if (!id) return toast('Enter a tracking ID', true);
  try {
    const t = await api('/track/' + encodeURIComponent(id));
    $('#' + outId).innerHTML = `<div class="notice"><b>${esc(t.trackingId)}</b> &middot; ${badge(t.status)}<br>
      ${esc(t.pickupAddress)} &rarr; ${esc(t.dropAddress)}${t.partnerName ? '<br>Delivery partner: ' + esc(t.partnerName) : ''}
      <ul class="timeline">${t.history.map(h => `<li><b>${h.status.replace(/_/g, ' ')}</b> - ${esc(h.note)}<br>
      <span class="muted small">${fmt(h.time)}</span></li>`).join('')}</ul></div>`;
  } catch (e) { $('#' + outId).innerHTML = ''; throw e; }
});

start();
