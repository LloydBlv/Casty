package pl.droidsonroids.casty;

import android.view.Menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.app.MediaRouteButton;

class CastyNoOp extends Casty {
    private final CastyPlayer castyPlayer;

    CastyNoOp() {
        castyPlayer = new CastyPlayerNoOp();
    }

    @Override
    public CastyPlayer getPlayer() {
        return castyPlayer;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void addMediaRouteMenuItem(@NonNull Menu menu) {
        //no-op
    }

    @Override
    public void setUpMediaRouteButton(@NonNull MediaRouteButton mediaRouteButton) {
        //no-op
    }

    @Override
    public Casty withMiniController() {
        return this;
    }

    @Override
    public void addMiniController() {
        //no-op
    }

    @Override
    public void setOnCastSessionUpdatedListener(@Nullable OnCastSessionUpdatedListener onCastSessionUpdatedListener) {
        //no-op
    }
}
