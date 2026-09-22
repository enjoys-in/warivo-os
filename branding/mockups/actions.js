// Makes the mockup controls actionable: dock vehicle buttons (headlight, horn, lock,
// speaker), toggles, ride-mode, quick-settings tiles, media transport and sliders.
(function () {
  var PLAY = 'M7 5v14l12-7z';
  var PAUSE = 'M7 5h3v14H7zM14 5h3v14h-3z';

  function ready(fn) {
    if (document.readyState !== 'loading') fn();
    else document.addEventListener('DOMContentLoaded', fn);
  }

  function pulse(el) {
    el.classList.remove('press');
    void el.offsetWidth; // restart animation
    el.classList.add('press');
  }

  function setBar(bar, e) {
    var r = bar.getBoundingClientRect();
    var x = (e.clientX - r.left) / r.width;       // r is post-transform, so ratio is correct
    x = Math.max(0, Math.min(1, x));
    var pct = (x * 100).toFixed(1) + '%';
    var fill = bar.querySelector('.f, .fill');
    var knob = bar.querySelector('.k, .knob');
    if (fill) fill.style.width = pct;
    if (knob) knob.style.left = pct;
  }

  ready(function () {
    document.addEventListener('click', function (e) {
      var el;

      // Dock vehicle controls — headlight, horn, lock, speaker/volume
      if ((el = e.target.closest('.ctrl'))) { el.classList.toggle('on'); pulse(el); return; }

      // Control-center bento tiles — dark = off, white = engaged, blush = accent.
      // An accent tile remembers it is one, so tapping it twice lights it blush again.
      if ((el = e.target.closest('.bt.sq, .bt.wd'))) {
        if (el.classList.contains('hot') || el.dataset.accent) {
          el.dataset.accent = '1';
          el.classList.toggle('hot');
        } else {
          el.classList.toggle('on');
        }
        pulse(el);
        return;
      }

      // Quick-settings tiles (Wi-Fi, Bluetooth, GPS, Kiosk) — keep tile + toggle in sync
      if ((el = e.target.closest('.qt'))) {
        var on = el.classList.toggle('on');
        var t = el.querySelector('.toggle');
        if (t) t.classList.toggle('on', on);
        return;
      }

      // Standalone toggles (settings/about rows)
      if ((el = e.target.closest('.toggle'))) { el.classList.toggle('on'); return; }

      // Ride-mode / segmented control (ECO · CITY · SPORT)
      if ((el = e.target.closest('.seg .s'))) {
        el.parentElement.querySelectorAll('.s').forEach(function (s) { s.classList.remove('on'); });
        el.classList.add('on');
        return;
      }

      // Media play / pause (big + mini)
      if ((el = e.target.closest('.play, .pbtn'))) {
        var p = el.querySelector('path');
        if (p) p.setAttribute('d', p.getAttribute('d') === PLAY ? PAUSE : PLAY);
        pulse(el);
        return;
      }

      // Transport ghosts / prev-next — visual feedback only
      if ((el = e.target.closest('.ctl'))) { pulse(el); return; }

      // Sliders and progress bars — click to set value
      if ((el = e.target.closest('.slider, .bar, .mbar'))) { setBar(el, e); return; }
    });
  });
})();
