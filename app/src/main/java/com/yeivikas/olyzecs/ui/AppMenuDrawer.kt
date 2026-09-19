package com.yeivikas.olyzecs.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeivikas.olyzecs.R
import com.yeivikas.olyzecs.ui.theme.BrandBlueLight
import com.yeivikas.olyzecs.ui.theme.BrandPurple
import com.yeivikas.olyzecs.ui.theme.BrandPurpleDeep
import com.yeivikas.olyzecs.ui.theme.BrandPurpleLight
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedDark
import com.yeivikas.olyzecs.ui.theme.SurfaceTintedElevated

/**
 * Botón de menú "premium": una insignia circular con degradado de marca
 * (morado → azul) y sombra propia, con las tres barras del ícono clásico
 * de hamburguesa adentro — en vez del típico ícono plano de una sola
 * tinta, para que se sienta a la altura del resto de la identidad visual
 * de Olyze. Va a la izquierda del título "Menu" en la barra superior.
 */
@Composable
fun PremiumMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false, ambientColor = BrandPurpleLight, spotColor = BrandPurpleLight)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(BrandPurpleLight, BrandBlueLight)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.5.dp)
        ) {
            Box(
                Modifier
                    .width(18.dp)
                    .height(2.2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
            Box(
                Modifier
                    .width(13.dp)
                    .height(2.2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.85f))
            )
            Box(
                Modifier
                    .width(18.dp)
                    .height(2.2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

/**
 * Contenido del drawer lateral que dispara [PremiumMenuButton]. Por ahora
 * solo trae la opción "Registro de errores" (ver [ErrorLogScreen]) — está
 * armado como una lista ([AppMenuEntry]) para que agregar más opciones más
 * adelante sea sumar un ítem, no rediseñar la pantalla.
 */
@Composable
fun AppDrawerContent(
    onErrorLogClick: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = Color.Transparent,
        modifier = Modifier.width(300.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SurfaceTintedDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- Encabezado de marca ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(BrandPurple, BrandPurpleDeep)))
                        .padding(horizontal = 20.dp, vertical = 28.dp)
                ) {
                    Text(
                        "Olyze Creation Studio",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Editor de video profesional",
                        color = Color(0xFFD6CFEF),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // --- Opciones ---
                AppMenuEntryRow(
                    iconRes = R.drawable.ic_bug_report,
                    label = "Registro de errores",
                    description = "Errores, avisos y fallos capturados en vivo",
                    onClick = {
                        onCloseDrawer()
                        onErrorLogClick()
                    }
                )

                Spacer(Modifier.weight(1f))

                Text(
                    "Olyze Creation Studio · YeiViKas Digital Company",
                    color = Color(0xFF6E5FA0),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AppMenuEntryRow(
    iconRes: Int,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceTintedElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color(0xFFE05C5C),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, color = Color(0xFF9A8EC4), fontSize = 11.sp)
        }
    }
}
