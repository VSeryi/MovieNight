package com.tmdb.movie.ui.detail

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tmdb.movie.R
import com.tmdb.movie.component.ErrorPage
import com.tmdb.movie.data.AccountState
import com.tmdb.movie.data.Cast
import com.tmdb.movie.data.Credits
import com.tmdb.movie.data.Genre
import com.tmdb.movie.data.ImageType
import com.tmdb.movie.data.ImagesData
import com.tmdb.movie.data.MediaType
import com.tmdb.movie.data.MovieDetails
import com.tmdb.movie.data.MovieImage
import com.tmdb.movie.data.Video
import com.tmdb.movie.ui.detail.component.MovieBackdropLayout
import com.tmdb.movie.ui.detail.component.MovieCastLayout
import com.tmdb.movie.ui.detail.component.MovieDetailImageComponent
import com.tmdb.movie.ui.detail.component.MovieDetailLoadingComponent
import com.tmdb.movie.ui.detail.component.MovieDetailMoreAction
import com.tmdb.movie.ui.detail.component.MovieMiddleLayout
import com.tmdb.movie.ui.detail.component.MovieOverviewLayout
import com.tmdb.movie.ui.detail.component.MovieVideoComponent
import com.tmdb.movie.ui.detail.sheet.AllCastsBottomSheet
import com.tmdb.movie.ui.detail.sheet.AllImagesBottomSheet
import com.tmdb.movie.ui.detail.sheet.AllVideosBottomSheet
import com.tmdb.movie.ui.detail.sheet.MediaListBottomSheet
import com.tmdb.movie.ui.detail.vm.AddListUiState
import com.tmdb.movie.ui.detail.vm.MediaListUiState
import com.tmdb.movie.ui.detail.vm.MovieDetailUiState
import com.tmdb.movie.ui.detail.vm.MovieDetailViewModel
import com.tmdb.movie.ui.theme.TMDBMovieTheme
import com.tmdb.movie.utils.playMediaVideo
import com.tmdb.movie.utils.shareTMDBMedia
import kotlinx.coroutines.flow.collectLatest

private data class MovieDetailShowState(
    val movieDetails: MovieDetails? = null,
    val isLoading: Boolean = false,
    val throwError: Boolean = false
)

@Composable
fun MovieDetailRoute(
    mediaId: Int,
    @MediaType mediaType: Int,
    movieFrom: Int,
    toLogin: () -> Unit,
    onCreateList: () -> Unit,
    onBackClick: (Boolean) -> Unit,
    onNavigateToPeopleDetail: (Int) -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {

    BackHandler { onBackClick(movieFrom == 0) }
    val context = LocalContext.current
    val config by viewModel.configStream.collectAsStateWithLifecycle()
    var imageType by remember { mutableIntStateOf(ImageType.BACKDROP) }
    var showMediaListBottomSheet by remember { mutableStateOf(false) }
    var showAllVideosBottomSheet by remember { mutableStateOf(false) }
    var showAllImagesBottomSheet by remember { mutableStateOf(false) }
    var showAllCastsBottomSheet by remember { mutableStateOf(false) }
    var allVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var allImages by remember { mutableStateOf<List<MovieImage>>(emptyList()) }
    var allCasts by remember { mutableStateOf<List<Cast>>(emptyList()) }
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val addListState by viewModel.addListState.collectAsStateWithLifecycle()
    val detailsUiState by viewModel.movieDetail.collectAsStateWithLifecycle()
    val mediaListUiState by viewModel.mediaListUiState.collectAsStateWithLifecycle()
    val movieImages: ImagesData? by viewModel.movieImages.collectAsStateWithLifecycle()

    LaunchedEffect(config.userData) {
        val sessionId = config.userData?.sessionId
        if (!sessionId.isNullOrEmpty()) {
            viewModel.requestAccountState(sessionId)
        }
    }

    LaunchedEffect(mediaListUiState) {
        if (mediaListUiState is MediaListUiState.Success) {
            showMediaListBottomSheet = true
        }
    }

    LaunchedEffect(addListState) {
        when (addListState) {
            is AddListUiState.Error -> {
                val message = if ((addListState as AddListUiState.Error).errorType == 1) {
                    context.getString(R.string.key_media_already_in_list)
                } else {
                    context.getString(R.string.key_add_list_error)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.resetAddListState()
            }

            AddListUiState.Idle -> {}
            AddListUiState.Success -> {
                Toast.makeText(context, context.getString(R.string.key_add_list_success), Toast.LENGTH_SHORT).show()
                showMediaListBottomSheet = false
                viewModel.resetAddListState()
            }
        }

    }

    if (showMediaListBottomSheet && mediaListUiState is MediaListUiState.Success) {
        MediaListBottomSheet(
            onDismiss = { showMediaListBottomSheet = false },
            mediaList = (mediaListUiState as MediaListUiState.Success).mediaList,
            onMediaListClick = { mediaList ->
                if (!config.isLogin()) {
                    toLogin()
                    return@MediaListBottomSheet
                }
                val sessionId = config.userData?.sessionId ?: ""
                viewModel.toggleAddMediaToList(sessionId, mediaId, mediaList.id)
            },
            onCreateList = {
                onCreateList()
                showMediaListBottomSheet = false
            },
        )
    }

    if (showAllVideosBottomSheet) {
        AllVideosBottomSheet(
            videos = allVideos,
            onVideoClick = { videoKey, isYouTuBe ->
                playMediaVideo(context, videoKey, isYouTuBe)
            },
            onBottomSheetDismiss = { showAllVideosBottomSheet = false },
        )
    }

    if (showAllImagesBottomSheet) {
        AllImagesBottomSheet(
            imageType = imageType,
            images = allImages,
            onBottomSheetDismiss = { showAllImagesBottomSheet = false },
            onBuildImage = { url, type ->
                config.buildImageUrl(type, url)
            },
        )
    }

    if (showAllCastsBottomSheet) {
        AllCastsBottomSheet(
            castList = allCasts,
            onBottomSheetDismiss = { showAllCastsBottomSheet = false },
            onPeopleDetail = onNavigateToPeopleDetail,
            onBuildImage = { url, _ ->
                config.buildImageUrl(ImageType.PROFILE, url)
            },
        )
    }

    MovieDetailScreen(
        mediaType = mediaType,
        movieDetailUiState = detailsUiState,
        movieImages = movieImages,
        accountState = accountState,
        onBackClick = { onBackClick(movieFrom == 0) },
        onBuildImage = { url, type ->
            config.buildImageUrl(type, url)
        },
        onVideoClick = { videoKey, isYouTuBe ->
            playMediaVideo(context, videoKey, isYouTuBe)
        },
        onRetry = viewModel::onRetry,
        onMoreCasts = {
            allCasts = it
            showAllCastsBottomSheet = true
        },
        onMoreVideos = {
            allVideos = it
            showAllVideosBottomSheet = true
        },
        onMoreImages = { type, images ->
            imageType = type
            allImages = images
            showAllImagesBottomSheet = true
        },
        onPeopleDetail = onNavigateToPeopleDetail,
        onFavorite = {
            if (!config.isLogin()) {
                toLogin()
                return@MovieDetailScreen
            }
            val favorite = accountState?.favorite ?: false
            val sessionId = config.userData?.sessionId
            val accountId = config.userData?.id
            if (!sessionId.isNullOrEmpty() && accountId != null) {
                viewModel.toggleFavorite(accountId, sessionId, !favorite)
            }
        },
        onWatchlist = {
            if (!config.isLogin()) {
                toLogin()
                return@MovieDetailScreen
            }
            val watchlist = accountState?.watchlist ?: false
            val sessionId = config.userData?.sessionId
            val accountId = config.userData?.id
            if (!sessionId.isNullOrEmpty() && accountId != null) {
                viewModel.toggleWatchlist(accountId, sessionId, !watchlist)
            }
        },
        onAddList = {
            if (!config.isLogin()) {
                toLogin()
                return@MovieDetailScreen
            }
            val accountId = config.userData?.id
            if (accountId != null) {
                viewModel.toggleGetMediaList(accountId)
            }
        },
        onShare = {
            shareTMDBMedia(context, mediaId, mediaType)
        },
    )
}

@Composable
fun MovieDetailScreen(
    @MediaType mediaType: Int,
    movieDetailUiState: MovieDetailUiState,
    movieImages: ImagesData?,
    accountState: AccountState?,
    onBackClick: (Boolean) -> Unit,
    onBuildImage: (String?, @ImageType Int) -> String? = { url, _ -> url },
    onVideoClick: (String?, Boolean) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    onMoreCasts: (List<Cast>) -> Unit,
    onMoreVideos: (List<Video>) -> Unit,
    onMoreImages: (@ImageType Int, List<MovieImage>) -> Unit,
    onPeopleDetail: (Int) -> Unit,
    onFavorite: () -> Unit,
    onWatchlist: () -> Unit,
    onAddList: () -> Unit,
    onShare: () -> Unit,
) {
    val title = if (movieDetailUiState is MovieDetailUiState.Success) {
        movieDetailUiState.movieDetails.getMovieName(mediaType) ?: ""
    } else {
        ""
    }

    val showState by produceState(key1 = movieDetailUiState, initialValue = MovieDetailShowState(isLoading = true)) {
        value = when (movieDetailUiState) {
            is MovieDetailUiState.Error -> MovieDetailShowState(throwError = true)
            MovieDetailUiState.Loading -> MovieDetailShowState(isLoading = true)
            is MovieDetailUiState.Success -> MovieDetailShowState(movieDetailUiState.movieDetails)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        val scrollState = rememberScrollState()
        AnimatedContent(
            targetState = showState,
            label = "",
            transitionSpec = {
                (fadeIn(animationSpec = tween(500)))
                    .togetherWith(fadeOut(animationSpec = tween(500)))
            }
        ) { targetState ->
            when {
                targetState.movieDetails != null -> {
                    MovieDetailComponent(
                        mediaType = mediaType,
                        movieDetails = targetState.movieDetails,
                        movieImages = movieImages,
                        onBuildImage = onBuildImage,
                        onVideoClick = onVideoClick,
                        onMoreCasts = onMoreCasts,
                        onMoreVideos = onMoreVideos,
                        onMoreImages = onMoreImages,
                        onPeopleDetail = onPeopleDetail,
                        scrollState = scrollState,
                    )
                }

                targetState.throwError -> {
                    ErrorPage(onRetry = onRetry)
                }

                else -> {
                    MovieDetailLoadingComponent()
                }
            }
        }
        MovieDetailTopBar(
            modifier = Modifier,
            title = title,
            scrollState = scrollState,
            accountState = accountState,
            onBackClick = onBackClick,
            onFavorite = onFavorite,
            onWatchlist = onWatchlist,
            onAddList = onAddList,
            onShare = onShare,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailTopBar(
    modifier: Modifier,
    title: String,
    scrollState: ScrollState,
    accountState: AccountState?,
    onBackClick: (Boolean) -> Unit,
    onFavorite: () -> Unit,
    onWatchlist: () -> Unit,
    onAddList: () -> Unit,
    onShare: () -> Unit,
) {
    var topBarAlpha by rememberSaveable { mutableFloatStateOf(0f) }
    var topBarHeight by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }
            .collectLatest { scrollValue ->
                val deltaY = scrollValue.toFloat().coerceAtMost(topBarHeight.toFloat())
                topBarAlpha = deltaY / topBarHeight.toFloat()
            }
    }

    TopAppBar(
        modifier = modifier.onGloballyPositioned {
            topBarHeight = it.size.height
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = topBarAlpha
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                onBackClick(true)
            }) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_arrow_back_24),
                    contentDescription = ""
                )
            }
        },
        actions = {
            IconButton(onClick = onFavorite) {
                Icon(
                    painter = painterResource(id = if (accountState?.favorite == true) R.drawable.baseline_favorite_24 else R.drawable.outline_favorite_24),
                    contentDescription = "",
                    tint = if (accountState?.favorite == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MovieDetailMoreAction(
                modifier = Modifier,
                accountState = accountState,
                onWatchlist = onWatchlist,
                onAddList = onAddList,
                onShare = onShare,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = topBarAlpha),
        )
    )
}

@Composable
fun MovieDetailComponent(
    @MediaType mediaType: Int,
    movieImages: ImagesData? = null,
    movieDetails: MovieDetails? = null,
    scrollState: ScrollState,
    onBuildImage: (String?, @ImageType Int) -> String? = { url, _ -> url },
    onVideoClick: (String?, Boolean) -> Unit = { _, _ -> },
    onMoreCasts: (List<Cast>) -> Unit,
    onMoreVideos: (List<Video>) -> Unit,
    onMoreImages: (@ImageType Int, List<MovieImage>) -> Unit,
    onPeopleDetail: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        MovieBackdropLayout(
            mediaType = mediaType,
            movieDetails = movieDetails,
            onBuildImage = onBuildImage
        )
        MovieMiddleLayout(
            modifier = Modifier.padding(bottom = 24.dp),
            mediaType = mediaType,
            movieDetails = movieDetails
        )
        MovieOverviewLayout(
            modifier = Modifier.padding(bottom = 24.dp),
            movieDetails = movieDetails,
            onBuildImage = onBuildImage
        )
        if (movieDetails?.credits?.cast?.isNotEmpty() == true) {
            MovieCastLayout(
                modifier = Modifier.padding(bottom = 24.dp),
                castList = movieDetails.credits.cast,
                onBuildImage = onBuildImage,
                onMoreCasts = onMoreCasts,
                onPeopleDetail = onPeopleDetail
            )
        }
        if (movieDetails?.videos?.results?.isNotEmpty() == true) {
            MovieVideoComponent(
                modifier = Modifier.padding(bottom = 24.dp),
                videos = movieDetails.videos.results,
                onVideoClick = onVideoClick,
                onMoreVideos = onMoreVideos,
            )
        }
        if (movieImages?.backdrops?.isNotEmpty() == true) {
            MovieDetailImageComponent(
                modifier = Modifier.padding(bottom = 24.dp),
                imageList = movieImages.backdrops,
                imageType = ImageType.BACKDROP,
                onBuildImage = onBuildImage,
                onMoreImages = onMoreImages
            )
        }
        if (movieImages?.posters?.isNotEmpty() == true) {
            MovieDetailImageComponent(
                modifier = Modifier.padding(bottom = 24.dp),
                imageList = movieImages.posters,
                imageType = ImageType.POSTER,
                onBuildImage = onBuildImage,
                onMoreImages = onMoreImages
            )
        }
    }
}


@Preview(showBackground = true)
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
fun MovieDetailScreenPreview() {
    val movieDetails = MovieDetails(
        title = "阿凡达：水之道",
        releaseDate = "2022-12-14",
        tagline = "传奇导演詹姆斯·卡梅隆全新巨作",
        genres = listOf(Genre(id = 1, name = "动作"), Genre(id = 2, name = "冒险"), Genre(id = 3, name = "科幻")),
        voteAverage = 7.655f,
        revenue = 232025.00,
        overview = "影片设定在《阿凡达》的剧情落幕十余年后，讲述了萨利一家（杰克、奈蒂莉和孩子们）的故事：危机未曾消散，一家人拼尽全力彼此守护、奋力求生，并历经艰险磨难。影片设定在《阿凡达》的剧情落幕十余年后，讲述了萨利一家（杰克、奈蒂莉和孩子们）的故事：危机未曾消散，一家人拼尽全力彼此守护、奋力求生，并历经艰险磨难。影片设定在《阿凡达》的剧情落幕十余年后，讲述了萨利一家（杰克、奈蒂莉和孩子们）的故事：危机未曾消散，一家人拼尽全力彼此守护、奋力求生，并历经艰险磨难。",
        credits = Credits(
            cast = listOf(
                Cast(name = "Sam Worthington", character = "Jake Sully", profilePath = "/mflBcox36s9ZPbsZPVOuhf6axaJ.jpg"),
                Cast(name = "Sam Worthington", character = "Jake Sully", profilePath = "/mflBcox36s9ZPbsZPVOuhf6axaJ.jpg"),
                Cast(name = "Sam Worthington", character = "Jake Sully", profilePath = "/mflBcox36s9ZPbsZPVOuhf6axaJ.jpg"),
            ),
        ),
        videos = com.tmdb.movie.data.Videos(
            results = listOf(
                com.tmdb.movie.data.Video(
                    key = "https://www.youtube.com/watch?v=5PSNL1qE6VY",
                    name = "阿凡达：水之道",
                    site = "YouTube",
                    type = "Trailer",
                ),
                com.tmdb.movie.data.Video(
                    key = "https://www.youtube.com/watch?v=5PSNL1qE6VY",
                    name = "阿凡达：水之道",
                    site = "YouTube",
                    type = "Trailer",
                ),
                com.tmdb.movie.data.Video(
                    key = "https://www.youtube.com/watch?v=5PSNL1qE6VY",
                    name = "阿凡达：水之道",
                    site = "YouTube",
                    type = "Trailer",
                ),
            )
        )
    )

    TMDBMovieTheme {
        MovieDetailScreen(
            mediaType = MediaType.MOVIE,
            movieDetailUiState = MovieDetailUiState.Success(movieDetails),
            movieImages = null,
            onBackClick = {},
            onBuildImage = { url, _ -> url },
            onVideoClick = { _, _ -> },
            onRetry = {},
            onMoreCasts = {},
            onMoreVideos = {},
            onMoreImages = { _, _ -> },
            onPeopleDetail = { },
            accountState = null,
            onFavorite = {},
            onWatchlist = {},
            onAddList = {},
            onShare = {},
        )
    }
}