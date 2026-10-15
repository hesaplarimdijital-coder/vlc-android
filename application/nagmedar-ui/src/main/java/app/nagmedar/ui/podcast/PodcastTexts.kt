package app.nagmedar.ui.podcast

import android.content.res.Resources
import app.nagmedar.ui.R

/** S-1101: what a failure means for the person, in words they can act on. */
internal fun FeedFailure.describe(res: Resources): String = when (this) {
    is FeedFailure.BadAddress -> res.getString(R.string.nag_pod_fail_address)
    is FeedFailure.Offline -> res.getString(R.string.nag_pod_fail_offline)
    is FeedFailure.Server -> when (status) {
        HTTP_NOT_FOUND, HTTP_GONE -> res.getString(R.string.nag_pod_fail_gone)
        OkHttpFeedSource.TOO_MANY_REDIRECTS -> res.getString(R.string.nag_pod_fail_redirects)
        else -> res.getString(R.string.nag_pod_fail_server, status)
    }
    is FeedFailure.Invalid -> res.getString(
        when (reason) {
            InvalidReason.NotXml -> R.string.nag_pod_fail_not_xml
            InvalidReason.NotFeed -> R.string.nag_pod_fail_not_feed
            InvalidReason.NoEpisodes -> R.string.nag_pod_fail_no_episodes
            InvalidReason.Empty -> R.string.nag_pod_fail_empty
        },
    )
}

private const val HTTP_NOT_FOUND = 404
private const val HTTP_GONE = 410

/** The word on a search that found nothing to show. */
internal fun SearchFailure.title(res: Resources): String = res.getString(
    when (this) {
        SearchFailure.Offline -> R.string.nag_pod_search_offline_title
        SearchFailure.Busy -> R.string.nag_pod_search_busy_title
        SearchFailure.Server -> R.string.nag_pod_search_failed_title
    },
)

internal fun SearchFailure.description(res: Resources): String = res.getString(
    when (this) {
        SearchFailure.Offline -> R.string.nag_pod_search_offline_desc
        SearchFailure.Busy -> R.string.nag_pod_search_busy_desc
        SearchFailure.Server -> R.string.nag_pod_search_failed_desc
    },
)

internal fun SearchNotice.text(res: Resources): String = res.getString(
    when (this) {
        SearchNotice.PodcastIndexMissing -> R.string.nag_pod_notice_no_index
        SearchNotice.PodcastIndexFailed -> R.string.nag_pod_notice_index_failed
    },
)

/** The OPML import's word when the file is no use. */
internal fun OpmlProblem?.describe(res: Resources): String = res.getString(
    when (this) {
        OpmlProblem.NotOpml -> R.string.nag_pod_opml_not_opml
        OpmlProblem.NoFeeds -> R.string.nag_pod_opml_no_feeds
        OpmlProblem.TooLarge -> R.string.nag_pod_opml_too_large
        null -> R.string.nag_pod_opml_unreadable
    },
)
