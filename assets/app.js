/* VCAM PRO — download centre data binding.
 *
 * The page reads release.json, which the deploy workflow regenerates from the
 * latest real GitHub release. When no release exists yet, the page says so
 * instead of inventing a version.
 */
(function () {
  'use strict';

  var REPO = 'MPV999/vcam-pro-ultimate';
  var RELEASES_URL = 'https://github.com/' + REPO + '/releases';
  var state = { release: null };

  function $(id) { return document.getElementById(id); }

  function formatBytes(bytes) {
    var value = Number(bytes) || 0;
    if (value <= 0) return '—';
    if (value >= 1073741824) return (value / 1073741824).toFixed(2) + ' GB';
    if (value >= 1048576) return (value / 1048576).toFixed(1) + ' MB';
    if (value >= 1024) return Math.round(value / 1024) + ' KB';
    return value + ' B';
  }

  function formatDate(iso) {
    if (!iso) return '—';
    var date = new Date(iso);
    if (isNaN(date.getTime())) return '—';
    return date.toLocaleDateString('vi-VN', { year: 'numeric', month: '2-digit', day: '2-digit' });
  }

  function escapeHtml(text) {
    return String(text || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  function render() {
    var release = state.release;
    var published = !!(release && release.published && release.version);

    var stateEl = $('dlState');
    var hintEl = $('dlHint');
    var button = $('dlButton');
    var heroVersion = $('heroVersion');

    if (!published) {
      if (stateEl) { stateEl.textContent = 'Chưa có bản phát hành'; stateEl.classList.add('pending'); }
      if (hintEl) {
        hintEl.textContent = 'Bản phát hành đang được CI dựng, ký và kiểm tra. Trang này sẽ tự cập nhật khi bản phát hành đầu tiên xuất hiện.';
      }
      if (button) { button.textContent = 'Xem trạng thái trên GitHub'; button.href = RELEASES_URL; }
      if (heroVersion) { heroVersion.textContent = 'Bản phát hành APK đang được chuẩn bị bởi GitHub Actions.'; }
      renderNotes(release);
      return;
    }

    if ($('dlVersion')) $('dlVersion').textContent = 'v' + release.version;
    if ($('dlCode')) $('dlCode').textContent = release.versionCode ? String(release.versionCode) : '—';
    if ($('dlFile')) $('dlFile').textContent = release.apkName || '—';
    if ($('dlSize')) $('dlSize').textContent = formatBytes(release.apkSize);
    if ($('dlMin')) $('dlMin').textContent = release.minAndroid || 'Android 10 (API 29)';
    if ($('dlAbi')) $('dlAbi').textContent = (release.abis || ['arm64-v8a', 'armeabi-v7a']).join(', ');
    if ($('dlDate')) $('dlDate').textContent = formatDate(release.releaseDate);
    if ($('dlSha')) $('dlSha').textContent = release.sha256 || '—';

    if (stateEl) {
      stateEl.textContent = release.signed === false ? 'Bản dựng chưa ký phát hành' : 'Đã phát hành';
      if (release.signed === false) stateEl.classList.add('pending');
    }

    if (button) {
      if (release.downloadUrl) {
        button.textContent = 'Tải APK ' + (release.apkName ? release.apkName : '');
        button.href = release.downloadUrl;
        button.setAttribute('rel', 'noopener');
      } else {
        button.textContent = 'Xem bản phát hành';
        button.href = RELEASES_URL;
      }
    }

    if (hintEl) {
      hintEl.textContent = release.signed === false
        ? 'APK này được ký bằng khóa debug của CI để có thể cài đặt ngay. Để phát hành chính thức cần cấu hình signing secrets (xem tài liệu BUILD.md).'
        : 'APK được CI dựng từ mã nguồn, ký release và kiểm tra bằng apksigner trước khi phát hành.';
    }

    if (heroVersion) {
      heroVersion.textContent = 'Phiên bản ' + release.version + (release.apkSize ? ' · ' + formatBytes(release.apkSize) : '') + ' · phát hành ' + formatDate(release.releaseDate);
    }
    if ($('heroDownload')) {
      $('heroDownload').href = release.downloadUrl || '#tai-ve';
      if (release.downloadUrl) $('heroDownload').setAttribute('rel', 'noopener');
    }
    if ($('dlReleaseLink')) $('dlReleaseLink').href = release.releaseUrl || RELEASES_URL;

    renderHistory(release);
    renderNotes(release);
  }

  function renderHistory(release) {
    var table = $('historyTable');
    if (!table) return;
    var body = table.querySelector('tbody');
    if (!body) return;
    if (!release || !release.published) return;
    body.innerHTML = '<tr><td>v' + escapeHtml(release.version) + '</td><td>' +
      escapeHtml(String(release.versionCode || '—')) + '</td><td>' +
      formatDate(release.releaseDate) + '</td><td>' +
      (release.signed === false ? 'Bản dựng CI, ký debug' : 'Bản phát hành chính thức') +
      '</td></tr>';
  }

  function renderNotes(release) {
    var notesEl = $('dlNotes');
    var releaseNotes = $('releaseNotes');
    var text = release && release.notes ? release.notes : '';
    if (!text) {
      var empty = '<p class="muted">Chưa có ghi chú phát hành.</p>';
      if (notesEl) notesEl.innerHTML = empty;
      if (releaseNotes) releaseNotes.innerHTML = empty;
      return;
    }
    var html = escapeHtml(text)
      .split(/\n{2,}/)
      .map(function (block) { return '<p>' + block.replace(/\n/g, '<br>') + '</p>'; })
      .join('');
    if (notesEl) notesEl.innerHTML = html;
    if (releaseNotes) releaseNotes.innerHTML = html;
  }

  function load() {
    fetch('release.json', { cache: 'no-store' })
      .then(function (response) {
        if (!response.ok) throw new Error('no release.json');
        return response.json();
      })
      .then(function (json) {
        state.release = json;
        render();
      })
      .catch(function () {
        state.release = { published: false };
        render();
      });
  }

  function wire() {
    var toggle = $('navToggle');
    var links = $('navLinks');
    if (toggle && links) {
      toggle.addEventListener('click', function () { links.classList.toggle('open'); });
      links.addEventListener('click', function (event) {
        if (event.target.tagName === 'A') links.classList.remove('open');
      });
    }

    var copy = $('copySha');
    if (copy) {
      copy.addEventListener('click', function () {
        var value = $('dlSha') ? $('dlSha').textContent.trim() : '';
        if (!value || value === '—') return;
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(value).then(function () {
            copy.textContent = 'Đã sao chép';
            setTimeout(function () { copy.textContent = 'Sao chép'; }, 1800);
          });
        }
      });
    }

    var year = $('year');
    if (year) year.textContent = String(new Date().getFullYear());

    load();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', wire);
  } else {
    wire();
  }
})();
