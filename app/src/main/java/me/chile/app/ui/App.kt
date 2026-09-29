package me.chile.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.chile.app.AppViewModel
import me.chile.app.domain.*

@Composable fun IconAction(glyph: Glyph,label: String,enabled: Boolean=true,onClick: ()->Unit) {
    val interaction=remember {androidx.compose.foundation.interaction.MutableInteractionSource()}
    IconButton(onClick=onClick,enabled=enabled,interactionSource=interaction,modifier=Modifier.size(48.dp).pressMotion(interaction).semantics {contentDescription=label}) {GlyphIcon(glyph,color=if(enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)}
}
@Composable fun ChileApp(vm: AppViewModel) {
    if(vm.loading){StartupShell();return}
    var routes by rememberSaveable {mutableStateOf(listOf("chat"))}
    val today=rememberToday()
    val data=vm.data
    val onboarding=!data.onboarded
    val homes=listOf("records","chat","trends")
    val pager=rememberPagerState(initialPage=1,pageCount={3})
    val scope=rememberCoroutineScope()
    val defaultConfig=LocalViewConfiguration.current
    val swipeConfig=remember(defaultConfig){object: ViewConfiguration by defaultConfig {override val touchSlop: Float get()=defaultConfig.touchSlop*.7f}}
    val density=LocalDensity.current
    val headerSwipe=Modifier.pointerInput(routes.last(),onboarding) {
        var distance=0f
        detectHorizontalDragGestures(onDragStart={distance=0f},onHorizontalDrag={change,amount->if(!onboarding){distance+=amount;change.consume()}},onDragEnd={
            if(!onboarding && (routes.last() in homes || routes.last()=="home") && kotlin.math.abs(distance)>with(density){32.dp.toPx()}) {
                val target=(pager.currentPage+if(distance<0)1 else -1).coerceIn(0,2)
                scope.launch{pager.animateScrollToPage(target)}
            }
        })
    }
    val focusManager=LocalFocusManager.current
    val atHome=routes.last() in homes || routes.last()=="home"
    val holder=rememberSaveableStateHolder()
    val page=if(routes.last()=="help") "help" else if(onboarding) if(data.profiles.isEmpty() || routes.last()=="profile") "profile" else "model" else if(atHome)homes[pager.settledPage] else routes.last()
    fun open(p: String){if(p in homes){routes=listOf("home");scope.launch{pager.animateScrollToPage(homes.indexOf(p))}}else routes=routes+p}
    fun back(){
        // Keep a covered form while help is open, but discard it when that visit ends.
        if(routes.last() !in homes && routes.last()!="home")holder.removeState(routes.last())
        routes=if(routes.size>1)routes.dropLast(1) else listOf("home")
    }
    BackHandler(routes.size>1 || (!onboarding && atHome && pager.currentPage!=1)) {if(routes.size>1)back() else scope.launch{pager.animateScrollToPage(1)}}
    BackHandler(onboarding && page=="model"){open("profile")}
    LaunchedEffect(pager.settledPage){focusManager.clearFocus()}
    val keyboard=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val snackbar=remember {SnackbarHostState()}
    LaunchedEffect(vm.notice){vm.notice?.let {snackbar.showSnackbar(it)}}
    var composerHeight by remember {mutableIntStateOf(0)}
    val homeVisible=!onboarding && atHome
    val chatDistance=kotlin.math.abs(pager.currentPage+pager.currentPageOffsetFraction-1f).coerceIn(0f,1f)
    Scaffold(modifier=Modifier.fillMaxSize().softBackdrop()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        containerColor=androidx.compose.ui.graphics.Color.Transparent,snackbarHost={SnackbarHost(snackbar)},
        topBar={if(!homeVisible)Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            if(!onboarding || routes.size>1 || page=="model")IconAction(Glyph.BACK,"返回"){if(onboarding && page=="model")open("profile") else back()}
            Text(when(page){"profile"->"身体数据";"model"->"连接 AI";"meal"->"记录这一餐";"help"->"使用说明";"memories"->"本地记忆";else->"设置"},style=MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if(page!="help")IconAction(Glyph.HELP,"帮助"){open("help")}
        }},contentWindowInsets=WindowInsets(0,0,0,0)) {padding->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding().navigationBarsPadding()) {
            if(homeVisible) {
                CompositionLocalProvider(LocalViewConfiguration provides swipeConfig) {
                    HorizontalPager(pager,modifier=Modifier.fillMaxSize(),beyondViewportPageCount=0,pageSpacing=12.dp,flingBehavior=PagerDefaults.flingBehavior(pager,snapPositionalThreshold=.18f),key={homes[it]}) {index->
                        CompositionLocalProvider(LocalViewConfiguration provides defaultConfig) {
                            Box(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
                                holder.SaveableStateProvider(homes[index]) {
                                    if(index==1)ChatScreen(vm,if(composerHeight>0)with(density){composerHeight.toDp()} else 88.dp)
                                    else Column(Modifier.fillMaxSize()) {
                                        Row(Modifier.fillMaxWidth().then(headerSwipe).heightIn(min=88.dp).padding(top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Column {Text(if(index==0)"饮食记录" else "热量看板",style=MaterialTheme.typography.headlineMedium);Text(today.format(java.time.format.DateTimeFormatter.ofPattern("M月d日  EEEE",java.util.Locale.CHINESE)),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                            Spacer(Modifier.weight(1f));if(index==2)IconAction(Glyph.SETTINGS,"设置"){open("settings")}
                                        }
                                        if(index==0)RecordsScreen(vm,active=pager.settledPage==0){vm.editMeal(it);open("meal")} else TrendsScreen(data.meals,exercises=data.exercises,profiles=data.profiles,overrides=data.activityOverrides)
                                    }
                                }
                            }
                        }
                    }
                }
                if(chatDistance<1f) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).padding(horizontal=16.dp)
                        .onSizeChanged {composerHeight=it.height}
                        .graphicsLayer {translationY=size.height*chatDistance;alpha=1f-chatDistance}) {
                        ChatComposer(vm,keyboard)
                    }
                }
            }else Box(Modifier.fillMaxSize().padding(horizontal=16.dp)) {holder.SaveableStateProvider(page) {when(page) {
                "profile"->ProfileScreen(vm){if(!onboarding || routes.last()=="profile")back()}
                "model"->ModelScreen(vm,onboarding){if(onboarding)vm.finishOnboarding() else back()}
                "meal"->MealScreen(vm){back()}
                "settings"->SettingsScreen(vm,::open)
                "memories"->MemoriesScreen(vm)
                "help"->HelpScreen()
            }}}
        }
    }
    vm.error?.let {message->AlertDialog(onDismissRequest=vm::clearError,title={Text("暂时无法完成")},text={Text(message)},confirmButton={TextButton(onClick=vm::clearError){Text("知道了")}})}
}
