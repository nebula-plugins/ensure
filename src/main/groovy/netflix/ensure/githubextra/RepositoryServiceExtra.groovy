package netflix.ensure.githubextra

import com.google.gson.reflect.TypeToken
import org.eclipse.egit.github.core.IRepositoryIdProvider
import org.eclipse.egit.github.core.RepositoryHook
import org.eclipse.egit.github.core.client.GitHubClient
import org.eclipse.egit.github.core.client.PagedRequest
import org.eclipse.egit.github.core.service.RepositoryService

import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_HOOKS
import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_REPOS

public class RepositoryServiceExtra extends RepositoryService {

    public RepositoryServiceExtra() {
        super();
    }

    public RepositoryServiceExtra(final GitHubClient client) {
        super(client);
    }

    @Override
    public RepositoryHookExtra createHook(
            final IRepositoryIdProvider repository, final RepositoryHook hook)
            throws IOException {
        final String id = getId(repository);
        final StringBuilder uri = new StringBuilder(SEGMENT_REPOS);
        uri.append('/').append(id);
        uri.append(SEGMENT_HOOKS);
        return client.post(uri.toString(), hook, RepositoryHookExtra.class);
    }

    public List<RepositoryHookExtra> getHooksExtra(
            final IRepositoryIdProvider repository) throws IOException {
        final String id = getId(repository);
        final StringBuilder uri = new StringBuilder(SEGMENT_REPOS);
        uri.append('/').append(id);
        uri.append(SEGMENT_HOOKS);
        final PagedRequest<RepositoryHookExtra> request = createPagedRequest();
        request.setUri(uri);
        request.setType(new TypeToken<List<RepositoryHookExtra>>() {
        }.getType());
        return getAll(request);
    }
}
