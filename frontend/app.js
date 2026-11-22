"use strict";

let products = [];
let featured = [];
let currentUser = null;
let sessionError = "";
let sessionNotice = "";
let sessionVersion = 0;
const catalogCategories = [["Audio", "nexo"], ["Periféricos", "trazo"], ["Pantallas y video", "marco24"], ["Accesorios", "puente"]];
const money = new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 });
const main = document.querySelector("main");
let filterCategory = "";
let searchText = "";
let sortOrder = "";
const maxQuantity = 99;
const cart = new Map();
let deliveryDraft = null;
let paymentDraft = "";
let purchaseSocket;
let socketRetry;
let liveUpdatesInterrupted = false;
const liveUpdatesMessage = "Las actualizaciones de tus pedidos pueden tardar un poco.";
let historyLoadVersion = 0;
let inbox = null;
let inboxOffset = 0;
let inboxVersion = 0;
const bellIcon = '<svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></svg>';
const deliveryFields = [
  ["recipient", "Nombre del destinatario", 100], ["phone", "Teléfono", 20],
  ["department", "Departamento", 60], ["city", "Ciudad o municipio", 80],
  ["address", "Dirección", 140], ["neighborhood", "Barrio", 80], ["notes", "Información adicional", 240]
];
const paymentMethods = { tarjeta: "Tarjeta terminada en 4242", contraentrega: "Pago contra entrega" };
const shippingStages = ["Pedido confirmado", "Preparando pedido", "Empacado", "En camino", "Entregado"];
const orderDate = new Intl.DateTimeFormat("es-CO", {dateStyle: "medium", timeStyle: "short", timeZone: "America/Bogota"});
const escapeHtml = value => String(value).replace(/[&<>"']/g, char => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
})[char]);
let feedbackTimer;
let quote = null;
let quoteVersion = 0;
let purchaseAttempt = null;
let purchaseReceipt = null;

const logo = '<a class="logo" href="#inicio" aria-label="E-commerce, inicio"><span class="brand-mark" aria-hidden="true"></span>E-commerce</a>';
const photo = product => `<img src="assets/products/${product.id}.jpg" alt="${product.name}" width="382" height="339">`;
const title = (name, subtitle) => `<div class="heading"><h1>${name}</h1><p>${subtitle}</p></div>`;
const empty = (heading, copy) => `<div class="empty"><h2>${heading}</h2><p>${copy}</p></div>`;
const cartIcon = '<svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 4h2l2.3 10h11l2-7H6"/><circle cx="9" cy="19" r="1"/><circle cx="18" cy="19" r="1"/></svg>';
const cartCount = () => [...cart.values()].reduce((total, quantity) => total + quantity, 0);
const cop = amount => money.format(amount) + " COP";
const discountThreshold = 100000;
const discountConditions = label => `<button class="text-link discount-terms" type="button" data-discount-terms aria-haspopup="dialog">${label}</button>`;
const discountHint = product => `<p class="discount-hint">${product.price >= discountThreshold
  ? "10 % de descuento aplicable" : "10 % al alcanzar $100.000 con este producto"}</p>`;
const stockLabels = {IN_STOCK: "Disponible", LOW_STOCK: "Pocas unidades", OUT_OF_STOCK: "Agotado", UNKNOWN: "Inventario no disponible"};
const verifiedLimit = product => Number.isInteger(product?.availableQuantity)
  ? Math.max(0, Math.min(maxQuantity, product.availableQuantity)) : 0;
const stockBadge = product => `<p class="availability" data-state="${product.availability || "UNKNOWN"}">${stockLabels[product.availability] || (currentUser ? "Consultando inventario" : "Consulta existencias al entrar")}${Number.isInteger(product.availableQuantity) ? " · " + product.availableQuantity + " disponibles" : ""}</p>`;

function navigation(view) {
  const inboxWasOpen = document.querySelector("#notification-menu")?.open;
  const link = (id, text) => `<a href="#${id}" ${view === id ? 'aria-current="page"' : ""}>${text}</a>`;
  document.querySelector("#header").innerHTML = `<div class="wrap nav">${logo}
    <nav aria-label="Tienda">${link("inicio", "Inicio")}${currentUser ? link("catalogo", "Catálogo") + link("pedidos", "Mis pedidos") : ""}</nav>
    <div class="account">${currentUser ? `<a class="cart-nav" href="#carrito"><span class="sr">Carrito, unidades:</span>${cartIcon}<span id="cart-count">${cartCount()}</span></a>
      <details class="notice-menu" id="notification-menu" ${inboxWasOpen ? "open" : ""}><summary class="cart-nav" aria-label="Notificaciones">
        ${bellIcon}<span id="notice-count" hidden></span></summary>
        <section class="notice-panel" aria-labelledby="notice-heading"><div class="notice-header"><h2 id="notice-heading">Notificaciones</h2>
        <div class="notice-actions"><button type="button" class="button outline" id="notice-all" disabled>Marcar todas como leídas</button>
        <button type="button" class="button outline" id="notice-delete-all" disabled>Eliminar todas</button></div></div>
        <div id="notice-items" aria-live="polite"><p>Cargando notificaciones…</p></div></section></details>
      <details class="user-menu"><summary><span id="customer-name"></span></summary><button class="button outline" id="logout" type="button">Cerrar sesión</button></details>` :
      `${link("login", "Iniciar sesión")}<a class="button dark" href="#registro">Registrarse</a>`}</div>
  </div>`;
  if (currentUser) document.querySelector("#customer-name").textContent = currentUser.name;
  if (currentUser) renderInbox();
  const message = document.querySelector("#service-message");
  message.textContent = sessionError || sessionNotice;
  message.hidden = !message.textContent;
}

function productAction(product) {
  return currentUser ? `<button class="button dark" type="button" data-add="${product.id}" ${!verifiedLimit(product) ? "disabled" : ""}>${verifiedLimit(product) ? "Agregar al carrito" : product.availableQuantity === 0 ? "Agotado" : "Inventario sin confirmar"}<span class="sr"> ${product.name}</span></button>` :
    `<a class="button dark" href="#compra/${product.id}">Comprar<span class="sr"> ${product.name}</span></a>`;
}

function cards(items, showDiscount = false) {
  return items.map(product => `<article class="product-card">
    <a class="product-image" href="#producto/${product.id}">${photo(product)}</a>
    <div class="product-info"><p class="eyebrow">${product.category}</p><h3><a href="#producto/${product.id}">${product.name}</a></h3>
    <strong>${cop(product.price)}</strong>${stockBadge(product)}${showDiscount ? discountHint(product) : ""}<div class="product-actions"><a class="button outline" href="#producto/${product.id}">Ver detalles<span class="sr"> de ${product.name}</span></a>${productAction(product)}</div></div>
    </article>`).join("") || empty("No encontramos productos", "Cambia la búsqueda o selecciona otra categoría.");
}

function home() {
  const homeProducts = featured.map(product => products.find(item => item.id === product.id) || product);
  return `<div class="wrap"><section class="hero">
    <div class="hero-copy"><p class="eyebrow">TECNOLOGÍA PARA TU ESCRITORIO</p><h1>Eleva tu escritorio.</h1>
    <p>Encuentra audio, pantallas y periféricos para tu próxima configuración.</p>
    <div class="hero-actions"><a class="button warm" href="#catalogo">Ver catálogo</a></div></div>
    <img class="hero-photo" src="assets/portada-azul.jpg" alt="Auriculares, teclado y mouse en un escritorio oscuro" width="1536" height="1024">
    </section>
    <section class="featured"><div class="section-head"><h2>Compra por categoría</h2></div>
    <div class="category-grid">${catalogCategories.map(([name, id], index) => `<a class="category-card" href="#catalogo/${index}"><img src="assets/products/${id}.jpg" alt="" width="382" height="339"><strong>${name}</strong></a>`).join("")}</div></section>
    <section class="discount-promo" aria-labelledby="discount-promo-title">
      <span class="discount-mark" aria-hidden="true">10 <small>%</small></span>
      <div><h2 id="discount-promo-title">Obtén un 10 % de descuento</h2>
      <p>Compra $100.000 COP o más de un mismo producto y recibe un descuento automático.</p></div>
      <div class="discount-actions"><a class="button dark" href="#catalogo">Explorar catálogo</a>
      ${discountConditions("Ver condiciones")}</div>
    </section>
    ${featured.length ? `<section class="featured"><div class="section-head"><div><h2>Productos destacados</h2><p>Precios en pesos colombianos.</p></div></div>
    <div class="grid">${cards(homeProducts.slice(0, 3), true)}</div></section>
    <section class="recommended"><div><h2>Accesorios para tu escritorio</h2><p>Conecta tus dispositivos, escucha con claridad y amplía tu espacio de trabajo.</p></div>
    <div class="recommend-items">${featured.slice(3).map(product => `<a href="#producto/${product.id}">${photo(product)}<strong>${product.name}</strong><small>${cop(product.price)}</small></a>`).join("")}</div></section>` :
      `<section class="featured">${empty(sessionError ? "No se pudieron cargar los productos" : "Cargando productos", "La selección de la tienda se obtiene del servidor.")}</section>`}</div>`;
}

function catalog() {
  return `<section class="wrap">${title("Catálogo", "Tecnología para tu escritorio y para llevar.")}
    <div class="catalog-controls"><label>Buscar producto<span class="search-field"><svg width="20" height="20" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m15 15 5 5"/></svg><input type="search" id="catalog-search" placeholder="Nombre o categoría"></span></label>
    <label>Categoría<select id="catalog-category"><option value="">Todas</option>${catalogCategories.map(([name]) => `<option>${name}</option>`).join("")}</select></label>
    <label>Orden<select id="catalog-sort"><option value="">Destacados</option><option value="low">Menor precio</option><option value="high">Mayor precio</option></select></label></div>
    <p class="count" id="catalog-count" role="status"></p><div class="grid" id="catalog-results"></div></section>`;
}

function drawCatalog() {
  const matches = products.filter(product => (!filterCategory || product.category === filterCategory) &&
    `${product.name} ${product.category}`.toLowerCase().includes(searchText.trim().toLowerCase()));
  if (sortOrder) matches.sort((a, b) => sortOrder === "low" ? a.price - b.price : b.price - a.price);
  document.querySelector("#catalog-count").textContent = matches.length + (matches.length === 1 ? " producto" : " productos");
  document.querySelector("#catalog-results").innerHTML = cards(matches);
}

function productDetail(product) {
  return `<section class="wrap content"><a class="back" href="#catalogo">Volver al catálogo</a>
    <div class="split"><div class="detail-photo">${photo(product)}</div><div class="detail-copy">
    <p class="eyebrow">${product.category}</p><h1>${product.name}</h1><strong class="price">${money.format(product.price)} COP</strong>
    <p>${product.description}</p>${stockBadge(product)}${discountHint(product)}
    ${currentUser && verifiedLimit(product) ? `<label>Cantidad solicitada<input id="requested-quantity" type="number" min="1" max="${verifiedLimit(product)}" step="1" value="1" required></label>` : ""}
    ${productAction(product)}
    </div></div></section>`;
}

function cartView(review) {
  const heading = review ? "Revisar compra" : "Mi carrito";
  const lines = products.filter(product => cart.has(product.id));
  if (!lines.length) {
    return `<section class="wrap">${title(heading, "Selecciona productos del catálogo.")}
      ${empty("Tu carrito está vacío", "Agrega un producto para comenzar.")}<a class="button dark" href="#catalogo">Explorar catálogo</a></section>`;
  }
  const subtotal = lines.reduce((sum, product) => sum + cart.get(product.id) * product.price, 0);
  return `<section class="wrap checkout-flow">${title(heading, review ? "Revisa tus productos, la entrega y el total cotizado." : "Ajusta las cantidades antes de revisar tu selección.")}
    ${review ? checkoutSteps(2) : ""}
    <div class="cart-layout">
      <div class="cart-lines">${lines.map(product => {
        const quantity = cart.get(product.id);
        return `<article class="panel cart-line">${photo(product)}
          <div class="line-copy"><h2><a href="#producto/${product.id}">${product.name}</a></h2><p>${cop(product.price)} por unidad</p>
          ${review ? `<p>Cantidad: ${quantity}</p>` : `<label>Cantidad <input type="number" min="1" max="${verifiedLimit(product)}" step="1" required value="${quantity}" data-quantity="${product.id}" ${!verifiedLimit(product) ? "disabled" : ""} aria-label="Cantidad de ${product.name}"></label>`}
          <p class="small line-saving" data-line-discount="${product.id}" hidden></p></div>
          <div class="line-end"><strong class="line-total">${cop(product.price * quantity)}</strong>${review ? "" : `<button type="button" class="text-link" data-remove="${product.id}">Eliminar<span class="sr"> ${product.name}</span></button>`}</div></article>`;
      }).join("")}
      ${review ? `<div class="panel checkout"><div><h2>Datos de entrega</h2>${deliverySummary(deliveryDraft)}</div>
        <div><h2>Método de pago</h2><p>${paymentMethods[paymentDraft]}</p></div>
        <div class="step-actions"><a class="back" href="#entrega">Editar entrega</a><a class="back" href="#pago">Editar pago</a><a class="back" href="#carrito">Volver al carrito</a></div></div>` :
        '<button class="text-link" type="button" data-cart-action="empty">Vaciar carrito</button>'}</div>
      <aside class="panel checkout cart-summary">
        <h2>Resumen</h2>
        ${review ? '<div class="buyer"><strong>Tu cuenta</strong><p id="buyer-name"></p><p id="buyer-email"></p></div>' : ""}
        <div class="totals-row"><span>Subtotal</span><strong id="cart-subtotal">${cop(subtotal)}</strong></div>
        <div id="quote-result" aria-live="polite"><p>Verificando existencias y descuentos…</p></div>
        ${review ? '<button class="button dark" type="button" data-cart-action="confirm" disabled>Finalizar compra</button><p id="confirm-feedback" class="callout error" role="alert" hidden></p><p class="small">Al confirmar, no se realizará ningún cargo bancario.</p>' :
          '<button class="button dark" type="button" data-cart-action="checkout" disabled>Continuar a entrega</button><a class="text-link" href="#catalogo">Seguir comprando</a>'}
        ${review ? "" : discountConditions("Ver condiciones del descuento")}
      </aside>
    </div></section>`;
}

const continuationPattern = /^(catalogo(?:\/[0-3])?|pedidos|carrito|entrega|pago|revision|(?:compra|producto)\/[a-z0-9-]{1,40})$/;

function checkoutSteps(active) {
  return `<ol class="checkout-steps" aria-label="Pasos de compra">${["Entrega", "Pago", "Revisión"].map((name, index) =>
    `<li ${index === active ? 'aria-current="step"' : ""}><span aria-hidden="true">${index < active ? "✓" : index + 1}</span>${name}</li>`).join("")}</ol>`;
}

function deliveryIssue(data) {
  for (const [key, label, max] of deliveryFields) {
    const value = data?.[key]?.trim() || "";
    if (key !== "notes" && !value) return [key, `Completa el campo ${label.toLowerCase()}.`];
    if (value.length > max) return [key, `${label} admite hasta ${max} caracteres.`];
    if (key === "phone" && (!/^\+?[0-9 ()-]+$/.test(value) || !/^\d{7,15}$/.test(value.replace(/\D/g, "")))) {
      return [key, "Escribe un teléfono válido de 7 a 15 dígitos."];
    }
  }
  return null;
}

function checkoutForm(view) {
  const delivery = view === "entrega";
  const draft = deliveryDraft || {recipient: currentUser.name};
  const fields = deliveryFields.map(([key, label, max]) => {
    const value = escapeHtml(draft[key] || "");
    return key === "notes" ? `<label class="delivery-wide">${label} (opcional)<textarea name="${key}" maxlength="${max}" rows="3">${value}</textarea></label>` :
      `<label>${label}<input name="${key}" type="${key === "phone" ? "tel" : "text"}" maxlength="${max}" value="${value}" required></label>`;
  }).join("");
  return `<section class="wrap checkout-flow">${title(delivery ? "Datos de entrega" : "Método de pago",
    delivery ? "Indica quién recibirá el pedido y en qué dirección." : "Elige cómo prefieres pagar.")}${checkoutSteps(delivery ? 0 : 1)}
    <div class="cart-layout"><form class="panel checkout" id="${delivery ? "delivery-form" : "payment-form"}" method="post" autocomplete="off" novalidate>
      ${delivery ? `<div class="delivery-fields">${fields}</div>` : `<fieldset class="payment-options"><legend>Método de pago</legend>
      <label class="payment-option"><input type="radio" name="payment" value="tarjeta" ${paymentDraft === "tarjeta" ? "checked" : ""}>
      <span><strong>${paymentMethods.tarjeta}</strong><small>Sin cargo bancario</small></span><span class="sample-card" aria-hidden="true">•••• 4242</span></label>
      <label class="payment-option"><input type="radio" name="payment" value="contraentrega" ${paymentDraft === "contraentrega" ? "checked" : ""}>
      <span><strong>${paymentMethods.contraentrega}</strong></span></label></fieldset>`}
      <p class="callout error" data-step-error role="alert" hidden></p><div class="step-actions">
      <a class="button outline" href="${delivery ? "#carrito" : "#entrega"}">Volver</a>
      <button class="button dark" type="submit">${delivery ? "Continuar al pago" : "Revisar compra"}</button></div></form>
    <aside class="panel checkout cart-summary"><h2>Resumen</h2><p>${cartCount()} unidades en el carrito</p>
      <div class="total"><strong>Total de productos</strong><strong>${cop(cartTotal())}</strong></div></aside></div></section>`;
}

function deliverySummary(data) {
  const {recipient, phone, address, neighborhood, city, department, notes} = data;
  return `<strong>${escapeHtml(recipient)}</strong><p>${escapeHtml(phone)}</p>
    <p>${escapeHtml(address)} · ${escapeHtml(neighborhood)}</p><p>${escapeHtml(city)}, ${escapeHtml(department)}</p>
    ${notes ? `<p>${escapeHtml(notes)}</p>` : ""}`;
}

const resultLabels = {PAYMENT_SUCCESS: "Método de pago validado", PAYMENT_FAILED: "No se pudo validar el pago",
  STOCK_RESERVED: "Inventario reservado", OUT_OF_STOCK: "Sin existencias", STOCK_OUT_OF_STOCK: "Sin existencias",
  EMAIL_SENT: "Aviso registrado"};
const issue = line => line.payment === "PAYMENT_FAILED" || ["OUT_OF_STOCK","STOCK_OUT_OF_STOCK"].includes(line.stock);
const orderLabel = order => order.lines.some(issue) ? "Con incidencias" :
  order.lines.some(line => line.publication === "UNCERTAIN") ? "Pendiente de confirmación" :
    order.lines.every(line => line.payment && line.stock && line.notification) ? "Procesado" : "En procesamiento";

function orderCards(orders) {
  return orders.length ? `<div class="demo-order-list">${orders.map(order => `<article class="panel demo-order">
    <div><h2>Compra ${escapeHtml(order.id.slice(0,8))}</h2><p>${orderDate.format(new Date(order.date))} · ${cop(order.total)}</p></div>
    <p class="demo-status">${orderLabel(order)}</p><p>${order.lines.map(line => `${line.quantity} × ${escapeHtml(line.name)}`).join(" · ")}</p>
    <div><a class="button outline" href="#seguimiento/${escapeHtml(order.id)}">Ver seguimiento</a></div></article>`).join("")}</div>` :
    `${empty("Todavía no tienes pedidos", "Cuando confirmes una compra, podrás seguirla desde aquí.")}
    <a class="button dark" href="#catalogo">Explorar catálogo</a>`;
}

function trackingView(order) {
  const ready = order.lines.every(line => line.stock === "STOCK_RESERVED" && line.payment === "PAYMENT_SUCCESS");
  const stage = ready ? 1 : 0;
  return `<section class="panel processing-live"><h2>Estado de los artículos · ${orderLabel(order)}</h2>
    <div class="technical-grid">${order.lines.map(line => `<article><h3>${line.quantity} × ${escapeHtml(line.name)}</h3>
      <dl>${[["Pago",line.payment],["Inventario",line.stock],["Notificación",line.notification]].map(([label, value]) =>
        `<div><dt>${label}</dt><dd>${Object.hasOwn(resultLabels, value) ? resultLabels[value] : "Pendiente de resultado"}</dd></div>`).join("")}</dl>
      ${line.publication === "UNCERTAIN" ? '<p class="callout error">Aún estamos confirmando este artículo. Consulta el historial antes de volver a comprar.</p>' : ""}
      <p>${cop(line.subtotal)} · Descuento calculado ${cop(line.discount)}</p></article>`).join("")}</div></section>
    <div class="cart-layout"><article class="panel checkout"><h2>Seguimiento del envío</h2>
      <ol class="shipping-timeline">${shippingStages.map((label, index) =>
        `<li class="${index < stage ? "completed-step" : ""}" ${index === stage ? 'aria-current="step"' : ""}>
          <span aria-hidden="true">${index < stage ? "✓" : index + 1}</span><strong>${label}</strong></li>`).join("")}</ol>
      <p class="small">${order.lines.some(issue) ? "El pedido tiene artículos que requieren revisión." : "Aún no hay novedades del envío."}</p></article>
    <aside class="panel checkout"><h2>Resumen del pedido</h2><p>${orderDate.format(new Date(order.date))}</p>
      <div class="totals-row"><span>Subtotal</span><strong>${cop(order.subtotal)}</strong></div>
      <div class="totals-row"><span>Descuento calculado</span><strong>${cop(order.discount)}</strong></div>
      <div class="total"><strong>Total del pedido</strong><strong>${cop(order.total)}</strong></div>
      <div>${deliverySummary(order.delivery)}</div><p>${paymentMethods[order.payment] || "Método de pago pendiente"}</p></aside></div>`;
}

function historyPage(id) {
  return `<section class="wrap content">${id ? '<a class="back" href="#pedidos">Volver a Mis pedidos</a>' : ""}
    ${title(id ? "Estado del pedido" : "Mis pedidos", id ? "Resultados por artículo" : "Historial de compras")}
    <p id="live-updates-status" class="small" role="status">${liveUpdatesInterrupted ? liveUpdatesMessage : ""}</p>
    <div id="history-results"><p>Cargando pedidos…</p></div></section>`;
}

async function loadHistory(id) {
  const target = document.querySelector("#history-results");
  if (!target) return;
  const version = ++historyLoadVersion;
  try {
    const response = await api("/api/me/purchases" + (id ? "/" + encodeURIComponent(id) : ""));
    if (target.isConnected && version === historyLoadVersion) target.innerHTML = id ? trackingView(response) : orderCards(response);
  } catch (error) {
    if (target.isConnected && version === historyLoadVersion) {
      target.textContent = error.status === 404 ? "No encontramos ese pedido en tu historial." : "No se pudo recuperar el historial. Intenta volver a la sección.";
    }
  }
}

function historyChanged(purchaseId) {
  const [view, id] = location.hash.slice(1).split("/");
  if (view === "pedidos" || view === "pedidos-vacios") loadHistory();
  if (view === "seguimiento" && (!purchaseId || purchaseId === id)) loadHistory(id);
}

function setLiveUpdatesInterrupted(interrupted) {
  liveUpdatesInterrupted = interrupted;
  const status = document.querySelector("#live-updates-status");
  if (status) status.textContent = interrupted ? liveUpdatesMessage : "";
}

function connectPurchases() {
  if (!currentUser || purchaseSocket) return;
  const endpoint = new URL("/ws/purchases", location.href);
  endpoint.protocol = location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(endpoint);
  purchaseSocket = socket;
  socket.addEventListener("open", () => {
    if (socket !== purchaseSocket) return;
    setLiveUpdatesInterrupted(false);
    historyChanged();
    refreshOpenStock();
    loadInbox();
  });
  socket.addEventListener("message", event => {
    if (socket === purchaseSocket) {
      const update = JSON.parse(event.data);
      if (update.purchaseId) {
        historyChanged(update.purchaseId);
        refreshOpenStock();
      }
      loadInbox();
    }
  });
  socket.addEventListener("close", () => {
    if (socket !== purchaseSocket) return;
    purchaseSocket = null;
    setLiveUpdatesInterrupted(true);
    socketRetry = setTimeout(() => {
      connectPurchases();
      historyChanged();
    }, 3000);
  });
}

function renderInbox(error = "") {
  const target = document.querySelector("#notice-items");
  if (!target) return;
  const count = document.querySelector("#notice-count");
  const unread = inbox?.unread || 0;
  count.hidden = unread === 0;
  count.textContent = unread > 99 ? "99+" : unread;
  count.parentElement.setAttribute("aria-label", `Notificaciones, ${unread} sin leer`);
  document.querySelector("#notice-all").disabled = unread === 0;
  document.querySelector("#notice-delete-all").disabled = !(inbox?.items.length || inboxOffset);
  if (!inbox) {
    target.textContent = error || "Cargando notificaciones…";
    return;
  }
  const list = inbox.items.map(item => `<li class="notice-item ${item.readAt ? "" : "notice-unread"}">
    <div class="notice-line"><strong>${escapeHtml(item.title)}</strong>${item.readAt ? "" : '<span class="notice-dot" aria-label="Sin leer"></span>'}
      <button type="button" class="button outline notice-delete" data-notice-delete="${escapeHtml(item.id)}" aria-label="Eliminar notificación" title="Eliminar notificación">×</button></div>
    <p>${escapeHtml(item.description)}</p><time datetime="${escapeHtml(item.createdAt)}">${orderDate.format(new Date(item.createdAt))}</time>
    <div class="notice-actions"><a class="text-link" href="#seguimiento/${encodeURIComponent(item.purchaseId)}">Ver pedido</a>
    ${item.readAt ? "" : `<button type="button" class="button outline" data-notice-read="${escapeHtml(item.id)}">Marcar como leída</button>`}</div></li>`).join("");
  target.innerHTML = `${error ? `<p class="callout error">${escapeHtml(error)}</p>` : ""}
    ${list ? `<ul class="notice-list">${list}</ul>` : '<p class="notice-empty">No tienes notificaciones.</p>'}
    ${inboxOffset || inbox.hasMore ? `<div class="notice-actions notice-pages">
    ${inboxOffset ? '<button type="button" class="button outline" data-notice-page="-25">Anteriores</button>' : ""}
    ${inbox.hasMore ? '<button type="button" class="button outline" data-notice-page="25">Más antiguas</button>' : ""}</div>` : ""}`;
}

async function loadInbox() {
  if (!currentUser) return;
  const owner = currentUser.id;
  const version = ++inboxVersion;
  try {
    const response = await api("/api/me/notifications?offset=" + inboxOffset);
    if (currentUser?.id !== owner || version !== inboxVersion) return;
    inbox = response;
    renderInbox();
  } catch {
    if (currentUser?.id === owner && version === inboxVersion) renderInbox("No se pudieron actualizar las notificaciones.");
  }
}

function account(register, product, context) {
  const suffix = context ? "/" + context : "";
  return `<section class="wrap account-layout"><div class="account-art"><span class="brand-lockup"><span class="brand-mark" aria-hidden="true"></span>E-commerce</span><h2>${register ? "Tu cuenta, tus pedidos." : "Todo tu catálogo, en un lugar."}</h2><p>Accede a las fichas, compara precios y vuelve al producto que estabas consultando.</p></div>
    <div class="panel form-panel"><h1>${register ? "Crear cuenta" : "Iniciar sesión"}</h1><p>${context ? "Identifícate para continuar." : "Accede a tu cuenta de E-commerce."}</p>
    ${product ? `<p class="callout">Producto seleccionado · ${product.name}</p>` : ""}
    <form id="account-form" method="post" data-register="${register}" data-context="${context}">
      ${register ? '<label>Nombre completo<input name="name" autocomplete="name" required maxlength="100"></label>' : ""}
      <label>Correo electrónico<input name="email" type="email" autocomplete="email" required maxlength="254" placeholder="nombre@ejemplo.com"></label>
      <label>Contraseña<span class="password"><input name="password" type="password" autocomplete="${register ? 'new-password' : 'current-password'}" required maxlength="72" ${register ? 'minlength="6" aria-describedby="password-hint"' : ''}><button type="button" id="password-toggle" aria-pressed="false">Mostrar</button></span></label>
      ${register ? '<label>Confirmar contraseña<input name="confirmation" type="password" autocomplete="new-password" required minlength="6" maxlength="72"></label><p class="small" id="password-hint">Usa una contraseña de al menos 6 caracteres y hasta 72 bytes.</p>' : ""}
      <button type="submit" class="button dark">${register ? "Crear mi cuenta" : "Iniciar sesión"}</button>
      <p id="form-feedback" class="callout error" role="alert" hidden></p>
    </form>
    <p>${register ? `<a class="text-link" href="#login${suffix}">Ya tengo una cuenta</a>` : `<a class="text-link" href="#registro${suffix}">Crear cuenta</a>`}</p>
    </div></section>`;
}

function route() {
  const [view = "inicio", ...path] = location.hash.slice(1).split("/");
  const [id] = path;
  const product = (currentUser ? products : featured).find(item => item.id === id);
  navigation(view || "inicio");
  if (view === "operaciones") {
    main.innerHTML = `<section class="wrap access-wrap"><div class="panel access-panel"><h1>Panel de operaciones</h1>
      <p>La tienda de clientes no da acceso a operaciones.</p><div><a class="button outline" href="#inicio">Volver a la tienda</a></div></div></section>`;
    document.title = "Panel de operaciones · E-commerce";
    return;
  }
  const storefrontRestricted = ["catalogo", "compra", "pedidos", "pedidos-vacios", "carrito", "entrega", "pago", "revision", "confirmacion", "seguimiento"].includes(view) ||
    (view === "producto" && !product);
  if (!currentUser && storefrontRestricted) {
    const target = [view, ...path].join("/");
    const fallback = ["pedidos-vacios", "seguimiento", "confirmacion"].includes(view) ? "pedidos" : "catalogo";
    location.replace("#login/" + (continuationPattern.test(target) ? target : fallback));
    main.innerHTML = `<section class="wrap content">${title("Acceso de clientes", "Inicia sesión para continuar.")}</section>`;
    return;
  }
  const context = path.join("/");
  const safeContext = continuationPattern.test(context) ? context : "";
  const accountProduct = featured.find(item => item.id === safeContext.split("/")[1]);
  if (currentUser && ["login", "registro"].includes(view)) {
    location.replace("#" + (safeContext || "catalogo"));
    return;
  }
  if (["entrega", "pago", "revision"].includes(view) && !cart.size) { location.replace("#carrito"); return; }
  if (["pago", "revision"].includes(view) && deliveryIssue(deliveryDraft)) { location.replace("#entrega"); return; }
  if (view === "revision" && !Object.hasOwn(paymentMethods, paymentDraft)) { location.replace("#pago"); return; }
  switch (view) {
    case "catalogo":
      if (!products.length) {
        main.innerHTML = `<section class="wrap">${title("Catálogo", sessionError ? "El catálogo no está disponible." : "Cargando los productos.")}</section>`;
        break;
      }
      main.innerHTML = catalog();
      if (/^[0-3]$/.test(id)) filterCategory = catalogCategories[Number(id)][0];
      document.querySelector("#catalog-search").value = searchText;
      document.querySelector("#catalog-category").value = filterCategory;
      document.querySelector("#catalog-sort").value = sortOrder;
      drawCatalog();
      break;
    case "producto":
    case "compra":
      main.innerHTML = product ? productDetail(product) :
        `<section class="wrap content">${title("Producto no disponible", "Elige otro artículo del catálogo.")}<a class="button dark" href="#catalogo">Ver catálogo</a></section>`;
      break;
    case "carrito":
    case "revision":
      main.innerHTML = cartView(view === "revision");
      if (document.querySelector("#buyer-name")) {
        document.querySelector("#buyer-name").textContent = currentUser.name;
        document.querySelector("#buyer-email").textContent = currentUser.email;
      }
      if (document.querySelector("#quote-result")) loadQuote();
      break;
    case "entrega":
    case "pago":
      main.innerHTML = checkoutForm(view);
      break;
    case "confirmacion": {
      if (purchaseReceipt?.id === id) {
        main.innerHTML = `<section class="wrap content"><div class="panel access-panel confirmation-panel">
          <span class="confirm-mark" aria-hidden="true">✓</span>
          ${title("Compra registrada", cop(purchaseReceipt.total))}
          <p>Tu compra quedó guardada. Los resultados se actualizarán a medida que se procese cada artículo.</p>
          <div><a class="button dark" href="#pedidos">Ir a Mis pedidos</a></div></div></section>`;
        break;
      }
      main.innerHTML = historyPage(id);
      loadHistory(id);
      break;
    }
    case "seguimiento":
      main.innerHTML = historyPage(id);
      loadHistory(id);
      break;
    case "registro": main.innerHTML = account(true, accountProduct, safeContext); break;
    case "login": main.innerHTML = account(false, accountProduct, safeContext); break;
    case "pedidos":
    case "pedidos-vacios":
      main.innerHTML = historyPage();
      loadHistory();
      break;
    default: main.innerHTML = home();
  }
  document.title = main.querySelector("h1").innerText.replace(/\s+/g, " ") + " · E-commerce";
}

main.addEventListener("input", event => {
  const control = event.target;
  if (control.id === "catalog-search") { searchText = control.value; drawCatalog(); }
  if (control.form?.id === "delivery-form") {
    deliveryDraft = Object.fromEntries(new FormData(control.form));
    control.removeAttribute("aria-invalid");
    control.form.querySelector("[data-step-error]").hidden = true;
  }
});

function cartTotal() {
  return products.reduce((total, product) => total + product.price * (cart.get(product.id) || 0), 0);
}

function cartFeedback(message) {
  const feedback = document.querySelector("#cart-feedback");
  clearTimeout(feedbackTimer);
  feedback.textContent = message;
  feedback.classList.toggle("sr", !message);
  if (message) feedbackTimer = setTimeout(() => cartFeedback(""), 2800);
}

main.addEventListener("change", event => {
  const control = event.target;
  if (control.form?.id === "payment-form") {
    paymentDraft = control.value;
    control.form.querySelector("[data-step-error]").hidden = true;
  }
  if (control.id === "catalog-category") { filterCategory = control.value; drawCatalog(); }
  if (control.id === "catalog-sort") { sortOrder = control.value; drawCatalog(); }
  const id = control.dataset.quantity;
  if (!currentUser || !cart.has(id)) return;
  if (!control.validity.valid) {
    control.value = cart.get(id);
    cartFeedback(`Máximo disponible para este artículo: ${control.max} unidades.`);
    return;
  }
  const quantity = control.valueAsNumber;
  cart.set(id, quantity);
  const product = products.find(item => item.id === id);
  control.closest(".cart-line").querySelector(".line-total").textContent = cop(product.price * quantity);
  const total = cop(cartTotal());
  document.querySelector("#cart-subtotal").textContent = total;
  document.querySelector("#cart-count").textContent = cartCount();
  loadQuote();
});

async function api(path, data, extraHeaders = {}) {
  if (location.protocol === "file:") throw new Error("Abre la tienda desde Order API para conectar con el servidor.");
  let response;
  try {
    const headers = { Accept: "application/json", ...extraHeaders };
    if (data !== undefined) {
      const token = await api("/api/auth/csrf");
      headers[token.headerName] = token.token;
      headers["Content-Type"] = "application/json";
    }
    response = await fetch(path, {
      method: data === undefined ? "GET" : "POST",
      headers, credentials: "same-origin", cache: "no-store",
      body: data === undefined ? undefined : JSON.stringify(data)
    });
  } catch (error) {
    if (error.status) throw error;
    throw new Error("No se pudo conectar con Order API. Revisa que el servidor local esté iniciado.");
  }
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload) {
    const error = new Error(payload?.error || "Order API no respondió con datos válidos.");
    error.status = response.status;
    throw error;
  }
  return payload;
}

async function loadQuote() {
  const target = document.querySelector("#quote-result");
  if (!target || document.querySelector('[data-cart-action="confirm"][aria-busy="true"]')) return;
  const button = document.querySelector('[data-cart-action="confirm"],[data-cart-action="checkout"]');
  const version = ++quoteVersion;
  const owner = currentUser?.id;
  const isCurrent = () => target.isConnected && version === quoteVersion && currentUser?.id === owner;
  quote = null;
  button.disabled = true;
  target.textContent = "Verificando existencias y descuentos…";
  main.querySelectorAll("[data-line-discount]").forEach(label => { label.hidden = true; });
  try {
    const response = await api("/api/catalog/quote", {items: [...cart].map(([sku, quantity]) => ({sku, quantity}))});
    if (!isCurrent()) return;
    quote = response;
    document.querySelector("#cart-subtotal").textContent = cop(response.subtotal);
    target.innerHTML = `<div class="totals-row"><span>Descuento total</span><strong>${response.discount > 0 ? "−" : ""}${cop(response.discount)}</strong></div>
      <div class="total"><strong>Total final cotizado</strong><strong id="checkout-total">${cop(response.total)}</strong></div>`;
    const discounts = new Map(response.lines.map(line => [line.sku, line.discount]));
    main.querySelectorAll("[data-line-discount]").forEach(label => {
      const discount = discounts.get(label.dataset.lineDiscount);
      label.textContent = discount > 0 ? "Descuento aplicado: −" + cop(discount) : "Sin descuento";
      label.classList.toggle("applied", discount > 0);
      label.hidden = false;
    });
    button.disabled = false;
  } catch (error) {
    if (!isCurrent()) return;
    target.innerHTML = `<p class="callout error">${escapeHtml(error.message)}</p>
      <button type="button" class="button outline" data-cart-action="requote">Actualizar cotización</button>`;
    if (error.status === 409 || error.status === 503) refreshStockLimits(isCurrent);
  }
}

async function refreshStockLimits(isCurrent) {
  let catalog;
  try {
    catalog = await api("/api/catalog/products");
  } catch {
    catalog = products.map(product => ({ ...product, availability: "UNKNOWN", availableQuantity: null }));
  }
  if (!isCurrent()) return;
  products = catalog;
  main.querySelectorAll("[data-quantity]").forEach(control => {
    const limit = verifiedLimit(products.find(product => product.id === control.dataset.quantity));
    control.max = limit;
    control.disabled = !limit;
  });
  if (isCatalogView()) route();
}

function isCatalogView() {
  return /^#(?:catalogo|producto|compra)(?:\/|$)/.test(location.hash);
}

function refreshOpenStock() {
  if (!currentUser) return;
  if (document.querySelector("#quote-result")) {
    loadQuote();
  } else if (isCatalogView()) {
    const owner = currentUser.id;
    const view = location.hash;
    refreshStockLimits(() => currentUser?.id === owner && location.hash === view);
  }
}

function clearCustomer() {
  currentUser = null;
  products = [];
  filterCategory = "";
  searchText = "";
  sortOrder = "";
  cart.clear();
  deliveryDraft = null;
  paymentDraft = "";
  ++inboxVersion;
  inbox = null;
  inboxOffset = 0;
  clearTimeout(socketRetry);
  const socket = purchaseSocket;
  purchaseSocket = null;
  socket?.close();
  setLiveUpdatesInterrupted(false);
  quote = null;
  purchaseAttempt = null;
  purchaseReceipt = null;
  cartFeedback("");
}

async function readSession() {
  const version = ++sessionVersion;
  const { user } = await api("/api/auth/session");
  if (version !== sessionVersion) return;
  if (!user) { clearCustomer(); return; }
  const catalog = await api("/api/catalog/products");
  if (version === sessionVersion) {
    if (currentUser && currentUser.id !== user.id) clearCustomer();
    currentUser = user;
    products = catalog;
    connectPurchases();
    loadInbox();
    const available = new Set(catalog.map(item => item.id));
    for (const id of cart.keys()) {
      if (!available.has(id)) cart.delete(id);
    }
  }
}

main.addEventListener("submit", async event => {
  const form = event.target;
  if (form.id === "delivery-form" || form.id === "payment-form") {
    event.preventDefault();
    if (!currentUser || !cart.size) return;
    const feedback = form.querySelector("[data-step-error]");
    if (form.id === "delivery-form") {
      deliveryDraft = Object.fromEntries([...new FormData(form)].map(([key, value]) => [key, value.trim()]));
      const issue = deliveryIssue(deliveryDraft);
      if (issue) {
        const [key, message] = issue;
        feedback.textContent = message;
        feedback.hidden = false;
        form.elements[key].setAttribute("aria-invalid", "true");
        form.elements[key].focus();
        return;
      }
      location.hash = "pago";
    } else if (Object.hasOwn(paymentMethods, paymentDraft)) {
      location.hash = "revision";
    } else {
      feedback.textContent = "Elige un método de pago.";
      feedback.hidden = false;
      form.querySelector("input").focus();
    }
    return;
  }
  if (form.id !== "account-form") return;
  event.preventDefault();
  const submit = form.querySelector('[type="submit"]');
  if (submit.disabled) return;
  const feedback = form.querySelector("#form-feedback");
  const register = form.dataset.register === "true";
  const data = Object.fromEntries(new FormData(form));
  const report = message => { feedback.textContent = message; feedback.hidden = false; };
  if (register && ([...data.password].length < 6 || new TextEncoder().encode(data.password).length > 72)) {
    report("Usa al menos 6 caracteres y hasta 72 bytes.");
    form.elements.password.focus();
    return;
  }
  if (register && data.password !== data.confirmation) {
    report("Las contraseñas deben coincidir.");
    form.elements.confirmation.focus();
    return;
  }
  submit.disabled = true;
  submit.textContent = register ? "Creando cuenta…" : "Iniciando sesión…";
  feedback.hidden = true;
  try {
    await api("/api/auth/" + (register ? "register" : "login"), data);
    form.reset();
    await readSession();
    if (!currentUser) throw new Error("No se pudo confirmar la sesión. Revisa que usas la URL HTTPS local.");
    sessionError = "";
    sessionNotice = register ? "Cuenta creada. Tu sesión está activa." : "Sesión iniciada.";
    location.replace("#" + (form.dataset.context || "catalogo"));
  } catch (error) {
    report(error.message);
  } finally {
    submit.disabled = false;
    submit.textContent = register ? "Crear mi cuenta" : "Iniciar sesión";
  }
});

document.querySelector("#header").addEventListener("click", async event => {
  const noticeLink = event.target.closest("#notice-items a");
  if (noticeLink) document.querySelector("#notification-menu").open = false;
  const notice = event.target.closest("#notice-all,#notice-delete-all,[data-notice-read],[data-notice-delete],[data-notice-page]");
  if (notice && currentUser && !notice.disabled) {
    if (notice.dataset.noticePage) {
      inboxOffset = Math.max(0, inboxOffset + Number(notice.dataset.noticePage));
      await loadInbox();
      return;
    }
    const deleteAll = notice.id === "notice-delete-all";
    const deleting = deleteAll || !!notice.dataset.noticeDelete;
    if (deleteAll && !window.confirm("¿Eliminar todas tus notificaciones?")) return;
    const owner = currentUser.id;
    notice.disabled = true;
    try {
      const action = deleteAll ? "delete-all" : notice.id === "notice-all" ? "read-all" :
        encodeURIComponent(notice.dataset.noticeDelete || notice.dataset.noticeRead) + (deleting ? "/delete" : "/read");
      await api("/api/me/notifications/" + action, {});
      if (currentUser?.id === owner) {
        if (deleting) inboxOffset = 0;
        await loadInbox();
      }
    } catch {
      if (currentUser?.id === owner) renderInbox(deleting ? "No se pudo eliminar. Intenta de nuevo." : "No se pudo marcar como leída. Intenta de nuevo.");
    }
    return;
  }
  const button = event.target.closest("#logout");
  if (!button || button.disabled) return;
  button.disabled = true;
  try {
    await api("/api/auth/logout", {});
    ++sessionVersion;
    clearCustomer();
    sessionError = "";
    sessionNotice = "Sesión cerrada.";
    route();
  } catch (error) {
    sessionError = error.message;
    navigation(location.hash.slice(1).split("/")[0]);
  }
});

main.addEventListener("click", async event => {
  if (event.target.closest("[data-discount-terms]")) {
    document.querySelector("#discount-policy").showModal();
    return;
  }
  const button = event.target.closest("button");
  if (!button) return;
  if (button.id === "password-toggle") {
    const input = button.previousElementSibling;
    const visible = input.type === "password";
    input.type = visible ? "text" : "password";
    button.textContent = visible ? "Ocultar" : "Mostrar";
    button.setAttribute("aria-pressed", visible);
  }
  if (!currentUser) return;
  if (button.dataset.add) {
    const id = button.dataset.add;
    const product = products.find(item => item.id === id);
    if (!product) return;
    const requested = document.querySelector("#requested-quantity");
    if (requested && !requested.reportValidity()) return;
    const quantity = (cart.get(id) || 0) + (requested?.valueAsNumber || 1);
    if (quantity > maxQuantity) { cartFeedback(`El máximo es de ${maxQuantity} unidades por producto.`); return; }
    if (quantity > verifiedLimit(product)) {
      cartFeedback(Number.isInteger(product.availableQuantity) ? "No hay existencias suficientes para esa cantidad." : "No pudimos verificar las existencias.");
      return;
    }
    cart.set(id, quantity);
    document.querySelector("#cart-count").textContent = cartCount();
    cartFeedback(`${product.name} añadido al carrito.`);
  }
  if (button.dataset.remove || button.dataset.cartAction === "empty") {
    if (button.dataset.remove) cart.delete(button.dataset.remove);
    else cart.clear();
    if (!cart.size) { deliveryDraft = null; paymentDraft = ""; }
    route();
    main.focus({ preventScroll: true });
    cartFeedback(button.dataset.remove ? "Producto eliminado." : "Carrito vacío.");
  }
  if (button.dataset.cartAction === "requote") loadQuote();
  if (button.dataset.cartAction === "checkout" && quote && !button.disabled) {
    await loadQuote();
    if (button.isConnected && quote) location.hash = "entrega";
  }
  if (button.dataset.cartAction === "confirm" && !button.disabled) {
    const owner = currentUser.id;
    const feedback = main.querySelector("#confirm-feedback");
    if (!quote || !cart.size || deliveryIssue(deliveryDraft) || !Object.hasOwn(paymentMethods, paymentDraft)) return;
    const body = {quote: {items: [...cart].map(([sku, quantity]) => ({sku, quantity}))}, delivery: deliveryDraft,
      payment: paymentDraft, expectedTotal: quote.total};
    const signature = JSON.stringify(body);
    if (purchaseAttempt?.signature !== signature) purchaseAttempt = {signature, key: crypto.randomUUID()};
    button.disabled = true;
    button.setAttribute("aria-busy", "true");
    button.textContent = "Registrando compra…";
    let needsQuote = false;
    try {
      const receipt = await api("/api/me/purchases", body, {"Idempotency-Key": purchaseAttempt.key});
      if (!button.isConnected || currentUser?.id !== owner) return;
      purchaseReceipt = receipt;
      cart.clear();
      deliveryDraft = null;
      paymentDraft = "";
      purchaseAttempt = null;
      location.hash = "confirmacion/" + receipt.id;
    } catch (error) {
      feedback.textContent = error.status ? error.message : "El resultado no se pudo confirmar. Reintenta esta misma compra para comprobarla sin duplicarla.";
      feedback.hidden = false;
      needsQuote = error.status === 409 || error.status === 503;
    } finally {
      button.removeAttribute("aria-busy");
      button.disabled = false;
      button.textContent = "Finalizar compra";
      if (button.isConnected && quote === null) button.disabled = true;
      if (button.isConnected && needsQuote) loadQuote();
    }
  }
});

document.querySelector(".skip").addEventListener("click", event => {
  event.preventDefault();
  main.focus();
});
window.addEventListener("hashchange", () => {
  route();
  if (isCatalogView()) refreshOpenStock();
  main.focus({ preventScroll: true });
  scrollTo(0, 0);
});
async function refresh() {
  const session = readSession();
  const selection = featured.length ? Promise.resolve() : api("/api/catalog/featured").then(items => { featured = items; });
  const [sessionResult, selectionResult] = await Promise.allSettled([session, selection]);
  if (sessionResult.status === "rejected") {
    clearCustomer();
  }
  const failed = [sessionResult, selectionResult].find(result => result.status === "rejected");
  sessionError = failed?.reason.message || "";
  route();
}

refresh();
window.addEventListener("pageshow", event => { if (event.persisted) refresh(); });
document.addEventListener("visibilitychange", async () => {
  if (!document.hidden && currentUser) await refresh();
});
