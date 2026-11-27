package com.cineconecta.platform.viewing.internal.service;

import com.cineconecta.platform.accounts.ProfileView;
import com.cineconecta.platform.billing.SubscriptionApi;
import com.cineconecta.platform.catalog.ICatalogApi;
import com.cineconecta.platform.catalog.PlayableRef;
import com.cineconecta.platform.viewing.internal.persistence.PlaybackSessionStore;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class PlaybackService {
    private final ViewingAccess access;
    private final ICatalogApi catalog;
    private final SubscriptionApi subscriptions;
    private final PlaybackSessionStore store;
    private final Clock clock;

    public PlaybackService(ViewingAccess access, ICatalogApi catalog, SubscriptionApi subscriptions,
                           PlaybackSessionStore store, Clock clock) {
        this.access = access;
        this.catalog = catalog;
        this.subscriptions = subscriptions;
        this.store = store;
        this.clock = clock;
    }

    public PlaybackSession start(int profileId, String contentId, String episodeId) {
        var profile = access.requireEligibleProfile(profileId).profile();
        String id = validId(contentId);
        String episode = episodeId == null || episodeId.isBlank() ? null : validId(episodeId);
        return startAt(profile, requirePlayable(profile, id, episode), 0);
    }

    private PlaybackSession startAt(ProfileView profile, PlayableRef title, int position) {
        Instant startedAt = now();
        int planId = subscriptions.planAt(profile.accountId(), startedAt)
                .orElseThrow(() -> new AccessDeniedException("Necesitas una suscripción vigente")).planId();
        var session = new PlaybackSession(new ObjectId().toHexString(), profile.accountId(), profile.profileId(),
                title.contentId(), title.episodeId(), title.type(), planId, title.durationSeconds(),
                startedAt, startedAt, null, 0, position, false, 0, startedAt);
        store.insert(session);
        return session;
    }

    public PlaybackSession find(int profileId, String sessionId) {
        return find(access.requireEligibleProfile(profileId).profile(), sessionId);
    }

    private PlaybackSession find(ProfileView profile, String sessionId) {
        if (sessionId == null || !ObjectId.isValid(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        PlaybackSession session = store.find(sessionId, profile.accountId(), profile.profileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlaybackSession closed = session.closeIfAbandoned(now());
        if (closed == session || store.saveAction(session, closed)) {
            return closed;
        }
        return store.find(sessionId, profile.accountId(), profile.profileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<ContinueItem> continueWatching(int profileId) {
        var profile = access.requireEligibleProfile(profileId).profile();
        var sessions = store.latestPerTitle(profile.accountId(), profileId).stream()
                .filter(session -> !session.completed() && session.lastPosition() > 0)
                .sorted(Comparator.comparing(PlaybackSession::lastActivityAt).reversed())
                .toList();
        var titles = catalog.findPlayables(sessions.stream().map(PlaybackSession::key).toList());

        return sessions.stream()
                .filter(session -> {
                    PlayableRef title = titles.get(session.key());
                    return title != null && title.ageRating() <= profile.maxAgeRating()
                            && (long) session.lastPosition() * 10 < (long) title.durationSeconds() * 9;
                })
                .map(session -> {
                    PlayableRef title = titles.get(session.key());
                    return new ContinueItem(session.id(), title.title(), title.episodeTitle(), session.lastPosition());
                })
                .toList();
    }

    public PlaybackSession resume(int profileId, String sessionId) {
        var profile = access.requireEligibleProfile(profileId).profile();
        var session = find(profile, sessionId);
        var title = requirePlayable(profile, session.contentId(), session.episodeId());
        if (session.completed() || session.lastPosition() <= 0
                || (long) session.lastPosition() * 10 >= (long) title.durationSeconds() * 9) {
            throw new IllegalArgumentException("Ya no hay una posición pendiente para este título.");
        }
        return session.endedAt() == null ? session : startAt(profile, title, session.lastPosition());
    }

    public boolean checkpoint(int profileId, String sessionId) {
        var session = find(profileId, sessionId);
        if (session.endedAt() != null) {
            return false;
        }
        var next = session.apply(PlaybackSession.Action.CHECKPOINT, null, now());
        if (next != session) {
            store.saveAction(session, next);
        }
        return true;
    }

    public void apply(int profileId, String sessionId, PlaybackSession.Action action, Integer position) {
        PlaybackSession session = find(profileId, sessionId);
        saveAction(session, session.apply(action, position, now()));
    }

    private void saveAction(PlaybackSession session, PlaybackSession next) {
        if (next != session && !store.saveAction(session, next)) {
            throw new IllegalArgumentException("La sesión cambió en otra pestaña. Intenta de nuevo.");
        }
    }

    public int currentPosition(PlaybackSession session) {
        return session.positionAt(clock.instant());
    }

    private PlayableRef requirePlayable(ProfileView profile, String id, String episode) {
        var title = catalog.findPlayable(id, episode)
                .orElseThrow(() -> new IllegalArgumentException("El título o episodio no está disponible."));
        if (title.ageRating() > profile.maxAgeRating()) {
            throw new IllegalArgumentException("La clasificación del título no está permitida para el perfil.");
        }
        return title;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    public record ContinueItem(String sessionId, String title, String episodeTitle, int position) {}

    private String validId(String value) {
        if (value == null || !ObjectId.isValid(value.strip())) {
            throw new IllegalArgumentException("Ingresa un ID de contenido o episodio válido.");
        }
        return new ObjectId(value.strip()).toHexString();
    }
}
