package netflix.ensure.githubextra;

import org.eclipse.egit.github.core.RepositoryHook;

// Via https://github.com/barchart/barchart-pivotal-github/tree/master/src/main/java/com/barchart/github

public class RepositoryHookExtra extends RepositoryHook {

    private static final long serialVersionUID = 1L;

    private volatile String[] events = new String[0];

    public RepositoryHookExtra setEvents(final String[] events) {
        this.events = events;
        return this;
    }

    public String[] getEvents() {
        return events;
    }

}