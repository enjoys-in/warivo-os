// Scales the fixed 1600×900 ".os" screen to fit the browser window (letterboxed),
// so any screen is fully visible when opened directly at any window size.
(function () {
  function fit() {
    var os = document.querySelector('.os');
    if (!os) return;
    var s = Math.min(window.innerWidth / 1600, window.innerHeight / 900);
    os.style.transform = 'scale(' + s + ')';
  }
  window.addEventListener('resize', fit);
  window.addEventListener('load', fit);
  document.addEventListener('DOMContentLoaded', fit);
  fit();
})();
