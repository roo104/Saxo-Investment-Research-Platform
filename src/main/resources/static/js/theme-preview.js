// theme-preview.js — live watchlist for the theme preview page.
//
// Data flow (see StreamController / ApiModels):
//   1. GET  /api/watchlist          -> [WatchlistEntryDto]  (names + baseline mid)
//   2. GET  /api/watchlist/stream   -> SSE "price" events of PriceTick (live mid)
// A PriceTick carries no symbol and no change figure, so names come from the REST
// list and "Change / %" are computed here against the first mid we observe.

(function () {
    "use strict";

    var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var SERIES_MAX = 40; // ticks retained per instrument for the sparkline

    // uic -> { id, symbol, description, baseline, last, series[], available }
    var rows = new Map();
    var order = []; // uic order, matching the REST list

    // ---- light / dark toggle (exercises the [data-theme="light"] override) ----
    var toggle = document.getElementById("theme-toggle");
    if (toggle) {
        toggle.addEventListener("click", function () {
            var light = document.documentElement.getAttribute("data-theme") === "light";
            if (light) {
                document.documentElement.removeAttribute("data-theme");
            } else {
                document.documentElement.setAttribute("data-theme", "light");
            }
            toggle.setAttribute("aria-pressed", String(!light));
            toggle.textContent = light ? "Light mode" : "Dark mode";
            order.forEach(renderRow); // redraw sparklines with the new theme's token colors
        });
    }

    // ---- connection status pill ----
    function setLive(cls, text) {
        var el = document.getElementById("live");
        if (!el) return;
        el.className = "live " + cls;
        el.textContent = text;
    }

    // ---- formatting helpers ----
    function midOf(t) {
        if (t.mid != null) return t.mid;
        if (t.bid != null && t.ask != null) return (t.bid + t.ask) / 2;
        return null;
    }

    function decimalsFor(v) {
        var a = Math.abs(v);
        return a >= 1000 ? 2 : a >= 10 ? 3 : 5;
    }

    function fmt(v, d) {
        return v == null ? "—" : v.toFixed(d);
    }

    // ---- sparkline from a real tick series ----
    function drawSpark(cell, series, color) {
        cell.innerHTML = "";
        if (series.length < 2) return;
        var c = document.createElement("canvas");
        var w = 84, h = 26, dpr = window.devicePixelRatio || 1;
        c.width = w * dpr;
        c.height = h * dpr;
        c.style.width = w + "px";
        c.style.height = h + "px";
        var ctx = c.getContext("2d");
        ctx.scale(dpr, dpr);

        var n = series.length;
        var min = Math.min.apply(null, series), max = Math.max.apply(null, series);
        var rng = (max - min) || 1;
        var x = function (i) {
            return (i / (n - 1)) * (w - 2) + 1;
        };
        var y = function (val) {
            return h - 3 - ((val - min) / rng) * (h - 6);
        };

        ctx.beginPath();
        ctx.moveTo(x(0), y(series[0]));
        series.forEach(function (p, i) {
            ctx.lineTo(x(i), y(p));
        });
        ctx.lineTo(x(n - 1), h);
        ctx.lineTo(x(0), h);
        ctx.closePath();
        ctx.fillStyle = color + "22";
        ctx.fill();

        ctx.beginPath();
        ctx.moveTo(x(0), y(series[0]));
        series.forEach(function (p, i) {
            ctx.lineTo(x(i), y(p));
        });
        ctx.strokeStyle = color;
        ctx.lineWidth = 1.5;
        ctx.lineJoin = "round";
        ctx.stroke();

        ctx.beginPath();
        ctx.arc(x(n - 1), y(series[n - 1]), 2, 0, Math.PI * 2);
        ctx.fillStyle = color;
        ctx.fill();
        cell.appendChild(c);
    }

    // ---- render one row from its current state ----
    function renderRow(uic) {
        var r = rows.get(uic);
        if (!r || !r.el) return;

        var css = getComputedStyle(document.documentElement);
        var gain = css.getPropertyValue("--color-gain").trim();
        var loss = css.getPropertyValue("--color-loss").trim();

        var lastCell = r.el.querySelector(".c-last");
        var chgCell = r.el.querySelector(".c-chg");
        var pctCell = r.el.querySelector(".c-pct");
        var sparkCell = r.el.querySelector(".spark");

        if (!r.available || r.last == null) {
            lastCell.textContent = "—";
            chgCell.textContent = "—";
            chgCell.className = "c-chg muted";
            pctCell.innerHTML = '<span class="muted">no quote</span>';
            sparkCell.innerHTML = "";
            return;
        }

        var d = decimalsFor(r.last);
        var base = r.baseline != null ? r.baseline : r.last;
        var change = r.last - base;
        var pct = base !== 0 ? (change / base) * 100 : 0;
        var up = change >= 0;
        var cls = up ? "gain" : "loss";
        var sign = up ? "+" : "";

        lastCell.textContent = fmt(r.last, d);
        chgCell.textContent = sign + change.toFixed(d);
        chgCell.className = "c-chg " + cls;
        pctCell.innerHTML = '<span class="chg ' + cls + '">' + sign + pct.toFixed(2) + "%</span>";
        drawSpark(sparkCell, r.series, up ? gain : loss);
    }

    function flash(r, up) {
        if (reduceMotion || !r.el) return;
        var cls = up ? "flash-up" : "flash-down";
        r.el.classList.remove("flash-up", "flash-down");
        // force reflow so re-adding the class restarts the animation
        void r.el.offsetWidth;
        r.el.classList.add(cls);
    }

    // ---- build the table body from the REST list ----
    function buildRows(list) {
        var tbody = document.getElementById("rows");
        tbody.innerHTML = "";
        rows.clear();
        order = [];

        if (!list.length) {
            tbody.innerHTML =
                '<tr><td colspan="5" class="empty">Watchlist is empty — add instruments via ' +
                "<code>POST /api/watchlist</code>.</td></tr>";
            return;
        }

        list.forEach(function (e) {
            var tr = document.createElement("tr");
            tr.innerHTML =
                '<td><span class="sym">' + e.symbol + "<small>" + e.description + "</small></span></td>" +
                '<td class="c-last">—</td>' +
                '<td class="c-chg muted">—</td>' +
                '<td class="c-pct"></td>' +
                '<td class="spark"></td>';
            tbody.appendChild(tr);

            rows.set(e.uic, {
                id: e.id,
                symbol: e.symbol,
                description: e.description,
                baseline: e.mid != null ? e.mid : null,
                last: e.mid != null ? e.mid : null,
                series: e.mid != null ? [e.mid] : [],
                available: e.priceAvailable,
                el: tr
            });
            order.push(e.uic);
            renderRow(e.uic);
        });
    }

    // ---- apply a live tick ----
    function onTick(ev) {
        var t;
        try {
            t = JSON.parse(ev.data);
        } catch (err) {
            return;
        }
        var r = rows.get(t.uic);
        if (!r) return; // instrument not in the list we rendered

        var mid = midOf(t);
        r.available = t.priceAvailable && mid != null;
        if (mid == null) {
            renderRow(t.uic);
            return;
        }

        var prev = r.last;
        if (r.baseline == null) r.baseline = mid; // first observed price = baseline
        r.last = mid;
        r.series.push(mid);
        if (r.series.length > SERIES_MAX) r.series.shift();

        renderRow(t.uic);
        if (prev != null && mid !== prev) flash(r, mid > prev);
    }

    // ---- open the SSE stream ----
    function connect() {
        setLive("connecting", "Connecting…");
        var es = new EventSource("/api/watchlist/stream");
        es.addEventListener("price", onTick);
        es.onopen = function () {
            setLive("on", "Live");
        };
        es.onerror = function () {
            // EventSource auto-reconnects; reflect the interruption meanwhile.
            setLive("error", "Reconnecting…");
        };
    }

    // ---- boot ----
    async function boot() {
        setLive("connecting", "Loading…");
        try {
            var res = await fetch("/api/watchlist", {headers: {Accept: "application/json"}});
            if (!res.ok) throw new Error("HTTP " + res.status);
            buildRows(await res.json());
        } catch (err) {
            var tbody = document.getElementById("rows");
            if (tbody) {
                tbody.innerHTML =
                    '<tr><td colspan="5" class="empty">Could not load watchlist (' +
                    String(err.message) + "). Is the backend running?</td></tr>";
            }
            setLive("error", "Offline");
            return;
        }
        connect();
    }

    boot();
})();
