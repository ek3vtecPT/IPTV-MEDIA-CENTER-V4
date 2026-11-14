package pt.iptvplayer.pro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun searchColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color(0xFF2B3654),
    focusedContainerColor = Surf,
    unfocusedContainerColor = Surf,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Accent
)

private fun allLabel(m: Mode) = when (m) {
    Mode.LIVE -> "Todos os canais"
    Mode.VOD -> "Todos os filmes"
    Mode.SERIES -> "Todas as séries"
}

private fun modeTitle(m: Mode) = when (m) {
    Mode.LIVE -> "Canais"
    Mode.VOD -> "Filmes"
    Mode.SERIES -> "Séries"
}

// =====================================================================
//  CATÁLOGO: categorias à esquerda + grelha/lista à direita
// =====================================================================

@Composable
fun CatalogScreen(vm: AppViewModel) {
    val mode = vm.catalogMode
    val wide = isWide()
    val items = remember(vm.data, mode, vm.categoryId, vm.query, vm.sort, vm.favorites, vm.history) {
        vm.visibleItems()
    }
    val cats = vm.data[mode]?.categories.orEmpty()
    val counts = remember(vm.data, mode) {
        vm.data[mode]?.items.orEmpty().groupingBy { it.catId }.eachCount()
    }
    val catNames = remember(cats) { cats.associate { it.id to it.name } }
    var menuItem by remember { mutableStateOf<Item?>(null) }

    val onOpen: (Int, Item) -> Unit = { index, item ->
        if (mode == Mode.SERIES && item.seriesId.isNotEmpty()) vm.openSeries(item)
        else vm.play(items, index, mode)
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenTopBar(modeTitle(mode), { vm.goBack() }) {
            Mode.values().forEach { m ->
                ModeTab(modeTitle(m), m == mode) { vm.changeCatalogMode(m) }
                Spacer(Modifier.width(6.dp))
            }
        }
        if (vm.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Color.Transparent)

        if (wide) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CategoryPanel(vm, mode, cats, counts, Modifier.width(280.dp).fillMaxHeight())
                CatalogContent(vm, mode, items, catNames, onOpen, { menuItem = it }, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CategoryChips(vm, mode, cats)
                CatalogContent(vm, mode, items, catNames, onOpen, { menuItem = it }, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    menuItem?.let { item ->
        PanelDialog(item.name, onDismiss = { menuItem = null }) {
            Text("Categoria: " + (catNames[item.catId] ?: "—"), color = TextSec, fontSize = 13.sp)
            if (mode == Mode.LIVE) Text("Programação indisponível", color = TextSec, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            OptionRow(
                if (vm.isFav(mode, item)) "Remover dos favoritos" else "Adicionar aos favoritos",
                false, { vm.toggleFav(mode, item); menuItem = null }
            )
            OptionRow(
                if (mode == Mode.SERIES && item.seriesId.isNotEmpty()) "Abrir série" else "Abrir em ecrã inteiro",
                false,
                {
                    val idx = items.indexOf(item).coerceAtLeast(0)
                    menuItem = null
                    onOpen(idx, item)
                }
            )
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .tvFocus(shape)
            .clip(shape)
            .background(if (selected) Selected else SurfHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun CategoryPanel(
    vm: AppViewModel, mode: Mode, cats: List<Category>, counts: Map<String, Int>, modifier: Modifier
) {
    var q by remember { mutableStateOf("") }
    val shown = remember(cats, q) {
        if (q.isBlank()) cats else cats.filter { it.name.contains(q.trim(), ignoreCase = true) }
    }
    val total = vm.data[mode]?.items?.size ?: 0
    val favCount = vm.favorites.count { it.startsWith(mode.name + ":") }

    Column(modifier = modifier.padding(start = 12.dp, end = 6.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            placeholder = { Text("Pesquisar categorias", color = TextSec) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = TextSec) },
            singleLine = true, shape = RoundedCornerShape(24.dp), colors = searchColors(),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
            item { CategoryRow(allLabel(mode), total, vm.categoryId == CAT_ALL) { vm.selectCategory(CAT_ALL) } }
            item { CategoryRow("Favoritos", favCount, vm.categoryId == CAT_FAV) { vm.selectCategory(CAT_FAV) } }
            item { CategoryRow("Histórico", null, vm.categoryId == CAT_HIST) { vm.selectCategory(CAT_HIST) } }
            items(shown.size) { i ->
                val c = shown[i]
                CategoryRow(c.name, counts[c.id], vm.categoryId == c.id) { vm.selectCategory(c.id) }
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocus(shape)
            .clip(shape)
            .background(if (selected) Selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), color = if (selected) Color.White else TextSec, fontSize = 12.sp)
    }
}

@Composable
private fun CategoryChips(vm: AppViewModel, mode: Mode, cats: List<Category>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ModeTab(allLabel(mode), vm.categoryId == CAT_ALL) { vm.selectCategory(CAT_ALL) } }
        item { ModeTab("Favoritos", vm.categoryId == CAT_FAV) { vm.selectCategory(CAT_FAV) } }
        item { ModeTab("Histórico", vm.categoryId == CAT_HIST) { vm.selectCategory(CAT_HIST) } }
        items(cats.size) { i ->
            val c = cats[i]
            ModeTab(c.name, vm.categoryId == c.id) { vm.selectCategory(c.id) }
        }
    }
}

@Composable
private fun CatalogContent(
    vm: AppViewModel,
    mode: Mode,
    items: List<Item>,
    catNames: Map<String, String>,
    onOpen: (Int, Item) -> Unit,
    onMenu: (Item) -> Unit,
    modifier: Modifier
) {
    var sortMenu by remember { mutableStateOf(false) }
    val title = when (vm.categoryId) {
        CAT_ALL -> allLabel(mode)
        CAT_FAV -> "Favoritos"
        CAT_HIST -> "Histórico"
        else -> catNames[vm.categoryId] ?: "Categoria"
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${items.size} itens", color = TextSec, fontSize = 12.sp)
            }
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it },
                placeholder = { Text("Pesquisar", color = TextSec) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = TextSec) },
                singleLine = true, shape = RoundedCornerShape(24.dp), colors = searchColors(),
                modifier = Modifier.weight(1.3f)
            )
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                if (vm.gridView) AppIcons.List else AppIcons.Grid,
                if (vm.gridView) "Mostrar em lista" else "Mostrar em grelha",
                { vm.gridView = !vm.gridView }, container = SurfHigh
            )
            Spacer(Modifier.width(8.dp))
            Box {
                CircleIconButton(AppIcons.Sort, "Ordenar", { sortMenu = true }, container = SurfHigh)
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf("Ordem original", "Nome A–Z", "Nome Z–A").forEachIndexed { i, label ->
                        DropdownMenuItem(
                            text = { Text(if (vm.sort == i) "✓ $label" else label) },
                            onClick = { vm.sort = i; sortMenu = false }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                items.isEmpty() -> {
                    val msg = when {
                        vm.busy -> "A carregar..."
                        vm.query.isNotBlank() -> "Sem resultados"
                        vm.categoryId == CAT_FAV -> "Ainda não tens favoritos. Prime prolongadamente um item para o adicionar."
                        vm.categoryId == CAT_HIST -> "O histórico está vazio."
                        else -> "Lista vazia"
                    }
                    EmptyState(AppIcons.Search, msg)
                }
                vm.gridView && mode == Mode.LIVE -> LiveGrid(vm, mode, items, onOpen, onMenu)
                vm.gridView -> PosterGrid(vm, mode, items, onOpen, onMenu)
                else -> ItemList(vm, mode, items, catNames, onOpen, onMenu)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveGrid(
    vm: AppViewModel, mode: Mode, items: List<Item>, onOpen: (Int, Item) -> Unit, onMenu: (Item) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items.size) { index ->
            val item = items[index]
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .tvFocus(shape)
                    .clip(shape)
                    .background(SurfHigh)
                    .combinedClickable(onClick = { onOpen(index, item) }, onLongClick = { onMenu(item) })
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RemoteImage(
                        item.icon, item.name,
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                        ContentScale.Fit
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.name, color = Color.White, fontSize = 12.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, lineHeight = 15.sp
                    )
                }
                if (vm.isFav(mode, item)) {
                    Icon(
                        AppIcons.Star, contentDescription = "Favorito", tint = Color(0xFFFFC107),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGrid(
    vm: AppViewModel, mode: Mode, items: List<Item>, onOpen: (Int, Item) -> Unit, onMenu: (Item) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items.size) { index ->
            val item = items[index]
            val shape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .tvFocus(shape)
                    .clip(shape)
                    .combinedClickable(onClick = { onOpen(index, item) }, onLongClick = { onMenu(item) })
            ) {
                RemoteImage(item.icon, item.name, Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                        .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp)
                ) {
                    Text(
                        item.name, color = Color.White, fontSize = 12.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, lineHeight = 15.sp, fontWeight = FontWeight.Medium
                    )
                }
                if (vm.isFav(mode, item)) {
                    Icon(
                        AppIcons.Star, contentDescription = "Favorito", tint = Color(0xFFFFC107),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemList(
    vm: AppViewModel, mode: Mode, items: List<Item>, catNames: Map<String, String>,
    onOpen: (Int, Item) -> Unit, onMenu: (Item) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items.size) { index ->
            val item = items[index]
            val shape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocus(shape)
                    .clip(shape)
                    .background(SurfHigh)
                    .combinedClickable(onClick = { onOpen(index, item) }, onLongClick = { onMenu(item) })
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mode == Mode.LIVE) {
                    Text((index + 1).toString(), color = TextSec, fontSize = 12.sp, modifier = Modifier.width(34.dp))
                }
                RemoteImage(
                    item.icon, item.name,
                    Modifier.size(width = 48.dp, height = if (mode == Mode.LIVE) 48.dp else 68.dp).clip(RoundedCornerShape(10.dp)),
                    if (mode == Mode.LIVE) ContentScale.Fit else ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(catNames[item.catId] ?: "", color = TextSec, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (vm.isFav(mode, item)) {
                    Icon(AppIcons.Star, contentDescription = "Favorito", tint = Color(0xFFFFC107), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// =====================================================================
//  DETALHES DA SÉRIE
// =====================================================================

@Composable
fun SeriesScreen(vm: AppViewModel) {
    val series = vm.seriesItem
    val seasons = remember(vm.episodes) { vm.episodes.map { it.season }.distinct() }
    val shown = remember(vm.episodes, vm.season) { vm.episodes.filter { it.season == vm.season } }
    val playList = remember(shown) { shown.map { Item(it.id, it.title, "", it.url) } }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenTopBar(series?.name ?: "Série", { vm.goBack() })
        if (vm.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Color.Transparent)
        if (series != null) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(series.icon, series.name, Modifier.size(width = 72.dp, height = 104.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(series.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (vm.episodes.isEmpty()) "Sem episódios" else "${seasons.size} temporadas · ${vm.episodes.size} episódios",
                        color = TextSec, fontSize = 13.sp
                    )
                }
            }
        }
        if (seasons.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(seasons.size) { i ->
                    val s = seasons[i]
                    ModeTab("Temporada $s", vm.season == s) { vm.season = s }
                }
            }
        }
        if (vm.episodes.isEmpty() && !vm.busy) {
            EmptyState(AppIcons.Series, "Esta série não tem episódios disponíveis.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(playList.size) { index ->
                    val ep = playList[index]
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus(shape)
                            .clip(shape)
                            .background(SurfHigh)
                            .clickable { vm.play(playList, index) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.PlayCircle, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(ep.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// =====================================================================
//  PESQUISA GLOBAL
// =====================================================================

@Composable
fun SearchScreen(vm: AppViewModel) {
    var results by remember { mutableStateOf<List<Pair<Mode, Item>>>(emptyList()) }
    LaunchedEffect(vm.globalQuery) {
        delay(250)
        results = withContext(Dispatchers.Default) { vm.globalResults() }
    }
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenTopBar("Pesquisar", { vm.goBack() })
        OutlinedTextField(
            value = vm.globalQuery, onValueChange = { vm.globalQuery = it },
            placeholder = { Text("Canais, filmes e séries", color = TextSec) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = TextSec) },
            singleLine = true, shape = RoundedCornerShape(28.dp), colors = searchColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when {
            vm.globalQuery.trim().length < 2 -> EmptyState(AppIcons.Search, "Escreve pelo menos 2 letras.")
            results.isEmpty() -> EmptyState(AppIcons.Search, "Sem resultados")
            else -> LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results.size) { i ->
                    val (m, item) = results[i]
                    val shape = RoundedCornerShape(16.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus(shape)
                            .clip(shape)
                            .background(SurfHigh)
                            .clickable {
                                if (m == Mode.SERIES && item.seriesId.isNotEmpty()) vm.openSeries(item)
                                else vm.play(listOf(item), 0, m)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RemoteImage(
                            item.icon, item.name,
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                            if (m == Mode.LIVE) ContentScale.Fit else ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(modeTitle(m), color = TextSec, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
