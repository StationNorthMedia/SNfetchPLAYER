document.addEventListener('DOMContentLoaded', () => {
  let lexiconData = [];
  const searchInput = document.getElementById('artistSearch');
  const searchDropdown = document.getElementById('searchDropdown');
  const featuredContainer = document.getElementById('featuredGrid');
  const artistDetailView = document.getElementById('artistDetail');

  // Top featured artists to display under search bar initially
  const FEATURED_NAMES = ['Mariah Carey', 'Erykah Badu', "D'Angelo", 'Alicia Keys', 'Sade', 'Usher'];

  // Utility: Clean display names like "Usher (musician)" -> "Usher"
  function getDisplayName(name) {
    return name ? name.replace(/\s*\([^)]*\)/g, '').trim() : '';
  }

  // Load lexicon data (supports file:// protocol via LEXICON_DATA or window.LEXICON_DATA or http fetch)
  const dataRef = (typeof LEXICON_DATA !== 'undefined') ? LEXICON_DATA : (window.LEXICON_DATA || null);
  if (dataRef && Array.isArray(dataRef) && dataRef.length > 0) {
    lexiconData = dataRef;
    initLexicon();
  } else {
    fetch('lexicon.json')
      .then(response => {
        if (!response.ok) throw new Error('Could not load lexicon.json');
        return response.json();
      })
      .then(data => {
        lexiconData = data;
        initLexicon();
      })
      .catch(err => {
        console.error('Error loading lexicon:', err);
        featuredContainer.innerHTML = `<div class="no-discography">Failed to load lexicon dataset. Make sure lexicon_data.js or lexicon.json is present.</div>`;
      });
  }

  function initLexicon() {
    initFeaturedArtists();
    
    // Select a random featured artist with an image and discography on every page load
    const candidates = lexiconData.filter(a => a.image && a.discography && a.discography.length > 0);
    const pool = candidates.length > 0 ? candidates : lexiconData;
    const randomArtist = pool[Math.floor(Math.random() * pool.length)];

    if (randomArtist) {
      renderArtistDetail(randomArtist);
    }
  }

  // Populate pre-listed featured artists
  function initFeaturedArtists() {
    featuredContainer.innerHTML = '';
    
    FEATURED_NAMES.forEach(name => {
      const artist = lexiconData.find(a => a.name.toLowerCase() === name.toLowerCase() || a.name.toLowerCase().includes(name.toLowerCase()));
      if (artist) {
        const card = createArtistCard(artist);
        featuredContainer.appendChild(card);
      }
    });
  }

  // Create card element for pre-listed featured grid
  function createArtistCard(artist) {
    const card = document.createElement('div');
    card.className = 'artist-card';
    
    const albumCount = artist.discography ? artist.discography.length : 0;
    const displayName = getDisplayName(artist.name);
    const avatarHtml = artist.image 
      ? `<img src="${artist.image}" alt="${displayName}" class="artist-card-avatar" onerror="this.onerror=null; this.src='logo.png'; if(!this.complete||this.naturalWidth==0)this.src='img/logo.png';">`
      : `<div class="artist-card-avatar-placeholder">${displayName.charAt(0)}</div>`;

    card.innerHTML = `
      ${avatarHtml}
      <div class="artist-card-info">
        <div class="artist-card-name">${displayName}</div>
        <div class="artist-card-meta">${albumCount} Albums recorded</div>
      </div>
    `;

    card.addEventListener('click', () => {
      renderArtistDetail(artist);
      searchInput.value = displayName;
      searchDropdown.classList.remove('active');
      
      // Smooth scroll down to details
      artistDetailView.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });

    return card;
  }

  // Render full artist detail view with expandable tracklists, streaming links & related artists
  function renderArtistDetail(artist, openAlbumTitle = '', highlightTrackName = '') {
    const displayName = getDisplayName(artist.name);
    const artistQuery = encodeURIComponent(displayName);
    const imageHtml = artist.image
      ? `<img src="${artist.image}" alt="${displayName}" class="artist-detail-image" onerror="this.onerror=null; this.src='logo.png'; if(!this.complete||this.naturalWidth==0)this.src='img/logo.png';">`
      : `<div class="artist-detail-image artist-card-avatar-placeholder" style="width:140px;height:140px;font-size:3rem;">${displayName.charAt(0)}</div>`;

    const streamingGroupHtml = `
      <div class="artist-streaming-group">
        <a href="https://www.youtube.com/results?search_query=${artistQuery}" target="_blank" rel="noopener noreferrer" class="artist-stream-btn yt">▶️ YouTube</a>
        <a href="https://www.deezer.com/search/${artistQuery}" target="_blank" rel="noopener noreferrer" class="artist-stream-btn dz">🎵 Deezer</a>
        <a href="https://open.spotify.com/search/${artistQuery}" target="_blank" rel="noopener noreferrer" class="artist-stream-btn sp">🎧 Spotify</a>
        <a href="https://music.apple.com/us/search?term=${artistQuery}" target="_blank" rel="noopener noreferrer" class="artist-stream-btn ap">🍎 Apple Music</a>
      </div>
    `;

    let discographyHtml = '';
    if (artist.discography && artist.discography.length > 0) {
      discographyHtml = `
        <div class="discography-grid">
          ${artist.discography.map((album, idx) => {
            const isTargetAlbum = openAlbumTitle && album.title && album.title.toLowerCase() === openAlbumTitle.toLowerCase();
            const isOpen = isTargetAlbum || (!openAlbumTitle && idx < 3);
            return `
              <details class="album-details-card" ${isOpen ? 'open' : ''} data-album-title="${(album.title || '').replace(/"/g, '&quot;')}">
                <summary class="album-summary-header">
                  <span class="album-year">${album.year || 'N/A'}</span>
                  <span class="album-name">${album.title}</span>
                  <span class="album-track-badge">🎵 ${album.tracks ? album.tracks.length : 0} Tracks</span>
                </summary>
                ${album.tracks && album.tracks.length > 0 ? `
                  <ol class="album-tracklist">
                    ${album.tracks.map(track => {
                      const trackQuery = encodeURIComponent(`${displayName} ${track}`);
                      const isTargetTrack = highlightTrackName && track.toLowerCase() === highlightTrackName.toLowerCase();
                      return `
                        <li ${isTargetTrack ? 'class="highlighted-track"' : ''}>
                          <div class="track-item-wrapper">
                            <span class="track-name">${track}</span>
                            <span class="track-actions">
                              <a href="https://www.youtube.com/results?search_query=${trackQuery}" target="_blank" rel="noopener noreferrer" class="track-action-btn" title="Search on YouTube">▶️ YT</a>
                              <a href="https://www.deezer.com/search/${trackQuery}" target="_blank" rel="noopener noreferrer" class="track-action-btn" title="Search on Deezer">🎵 DZ</a>
                              <a href="https://open.spotify.com/search/${trackQuery}" target="_blank" rel="noopener noreferrer" class="track-action-btn" title="Search on Spotify">🎧 SP</a>
                            </span>
                          </div>
                        </li>
                      `;
                    }).join('')}
                  </ol>
                ` : '<p class="no-tracks">No tracklist registered in archive.</p>'}
              </details>
            `;
          }).join('')}
        </div>
      `;
    } else {
      discographyHtml = `<p class="no-discography">No studio album records registered in archive.</p>`;
    }

    let relatedHtml = '';
    if (artist.related_artists && artist.related_artists.length > 0) {
      const cards = artist.related_artists.slice(0, 6).map(rel => {
        const relObj = lexiconData.find(a => a.id === rel.id) || rel;
        const relImg = relObj.image || 'logo.png';
        return `
          <div class="related-card" data-id="${rel.id}" style="cursor:pointer;">
            <img src="${relImg}" alt="${rel.name}" class="related-card-avatar" onerror="this.onerror=null; this.src='logo.png';">
            <span class="related-card-name">${rel.name}</span>
          </div>
        `;
      }).join('');
      relatedHtml = `
        <div class="related-section">
          <div class="discography-title">✨ Similar R&B / Soul Artists</div>
          <div class="related-grid">${cards}</div>
        </div>
      `;
    }

    artistDetailView.innerHTML = `
      <div class="artist-detail-card">
        <div class="artist-detail-header">
          ${imageHtml}
          <div class="artist-detail-title-group">
            <div class="artist-tags">
              <span class="artist-tag">R&B / Soul</span>
              <span class="artist-tag">Lexicon Entity</span>
            </div>
            <h2>${displayName}</h2>
            ${streamingGroupHtml}
          </div>
        </div>
        
        <div class="artist-bio">
          ${artist.bio || 'No English overview available.'}
        </div>

        <div class="discography-section">
          <div class="discography-title">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <circle cx="12" cy="12" r="3"></circle>
            </svg>
            Studio Albums & Complete Tracklists (${artist.discography ? artist.discography.length : 0})
          </div>
          ${discographyHtml}
        </div>

        ${relatedHtml}
      </div>
    `;

    // Attach click handlers to related artist cards
    document.querySelectorAll('.related-card[data-id]').forEach(card => {
      card.addEventListener('click', () => {
        const id = card.getAttribute('data-id');
        const target = lexiconData.find(a => a.id === id);
        if (target) {
          renderArtistDetail(target);
          searchInput.value = getDisplayName(target.name);
          artistDetailView.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
      });
    });

    // Scroll to highlighted track if search matched a song
    if (highlightTrackName) {
      setTimeout(() => {
        const hTrack = artistDetailView.querySelector('.highlighted-track');
        if (hTrack) {
          hTrack.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }, 100);
    }
  }

  // Handle Search Input Filtering (Artists, Albums & Track titles)
  searchInput.addEventListener('input', (e) => {
    const rawQuery = e.target.value.trim();
    const query = rawQuery.toLowerCase();
    
    if (query.length < 1) {
      searchDropdown.classList.remove('active');
      searchDropdown.innerHTML = '';
      return;
    }

    const results = [];
    const maxResults = 12;

    for (const artist of lexiconData) {
      if (results.length >= maxResults) break;

      const cleanArtistName = getDisplayName(artist.name);
      const cleanArtistLower = cleanArtistName.toLowerCase();
      const rawNameLower = artist.name.toLowerCase();

      // 1. Check Artist Name Match
      if (cleanArtistLower.includes(query) || rawNameLower.includes(query)) {
        results.push({
          type: 'artist',
          artist: artist,
          title: cleanArtistName,
          badge: `🎤 Artist (${artist.discography ? artist.discography.length : 0} Albums)`
        });
      }

      // 2. Check Albums & Songs if query is >= 2 chars
      if (query.length >= 2 && artist.discography && Array.isArray(artist.discography)) {
        for (const album of artist.discography) {
          if (results.length >= maxResults) break;

          const albumTitle = album.title || '';
          const albumTitleLower = albumTitle.toLowerCase();
          
          // Album Match
          if (albumTitleLower.includes(query) && !results.some(r => r.type === 'album' && r.title === albumTitle && r.artist.id === artist.id)) {
            results.push({
              type: 'album',
              artist: artist,
              albumTitle: albumTitle,
              title: albumTitle,
              badge: `💿 Album by ${cleanArtistName} (${album.year || 'N/A'})`
            });
          }

          // Track Matches
          if (album.tracks && Array.isArray(album.tracks)) {
            for (const track of album.tracks) {
              if (results.length >= maxResults) break;
              const trackLower = track.toLowerCase();

              if (trackLower.includes(query) && !results.some(r => r.type === 'track' && r.trackName === track && r.artist.id === artist.id)) {
                results.push({
                  type: 'track',
                  artist: artist,
                  albumTitle: albumTitle,
                  trackName: track,
                  title: track,
                  badge: `🎵 Song by ${cleanArtistName}`
                });
              }
            }
          }
        }
      }
    }

    if (results.length === 0) {
      searchDropdown.innerHTML = `<div class="dropdown-item" style="cursor:default; opacity: 0.7;">No artists, albums or songs matching "${rawQuery}"</div>`;
      searchDropdown.classList.add('active');
      return;
    }

    searchDropdown.innerHTML = results.map(res => `
      <div class="dropdown-item" data-id="${res.artist.id}" data-type="${res.type}" data-album="${encodeURIComponent(res.albumTitle || '')}" data-track="${encodeURIComponent(res.trackName || '')}">
        <span class="dropdown-item-name">${res.title}</span>
        <span class="dropdown-item-albums">${res.badge}</span>
      </div>
    `).join('');

    searchDropdown.classList.add('active');

    // Attach click listeners to dropdown items
    document.querySelectorAll('.dropdown-item[data-id]').forEach(item => {
      item.addEventListener('click', () => {
        const id = item.getAttribute('data-id');
        const targetAlbum = decodeURIComponent(item.getAttribute('data-album') || '');
        const targetTrack = decodeURIComponent(item.getAttribute('data-track') || '');
        const selected = lexiconData.find(a => a.id === id);

        if (selected) {
          renderArtistDetail(selected, targetAlbum, targetTrack);
          searchInput.value = item.querySelector('.dropdown-item-name').textContent;
          searchDropdown.classList.remove('active');
          artistDetailView.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      });
    });
  });

  // Trigger Live Search preview on focus
  searchInput.addEventListener('focus', () => {
    if (searchInput.value.trim().length > 0) {
      searchInput.dispatchEvent(new Event('input'));
    }
  });

  // Close dropdown on click outside
  document.addEventListener('click', (e) => {
    if (!searchInput.contains(e.target) && !searchDropdown.contains(e.target)) {
      searchDropdown.classList.remove('active');
    }
  });
});
