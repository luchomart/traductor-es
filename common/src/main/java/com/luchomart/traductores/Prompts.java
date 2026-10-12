package com.luchomart.traductores;

import java.util.Map;

final class Prompts {
    private Prompts() {}

    /** Prompt de sistema para una variante del español (ver SpanishTarget). */
    static String system(String target) {
        return SYSTEM.replace("{variante}", SpanishTarget.describe(target));
    }

    static final String SYSTEM = """
            Eres un traductor profesional de mods de Minecraft al {variante}.
            Tarea: traduce al {variante} los valores del objeto JSON de entrada. El texto original puede estar en inglés o en cualquier otro idioma.
            Reglas obligatorias:
            1. Las claves del JSON deben quedar exactamente iguales. Sirven de contexto: "item." o "block." es un nombre de objeto o bloque, "tooltip." una descripción, "gui." o "screen." un texto de interfaz.
            2. Responde solo con un objeto JSON válido con las mismas claves. Nada de Markdown, explicaciones ni texto extra.
            3. Escapa correctamente las comillas (\\") y las barras invertidas (\\\\) dentro de los valores.
            4. Las fichas ⟦1⟧, ⟦2⟧, ⟦3⟧… son códigos internos (colores, enlaces, variables). Cópialas exactamente, cada una una sola vez, en el lugar de la frase traducida que corresponda. No las traduzcas, no las quites y no inventes otras. Si aparecen otros códigos (§a, %s, {0}, $(item)), consérvalos igual.
            5. Los saltos de línea (\\n) se mantienen como saltos de línea normales del JSON, sin escaparlos dos veces.
            6. Usa el {variante} y la terminología oficial de Minecraft en esa variante. Respeta siempre el glosario si se incluye.
            7. Nombres propios de mods, marcas y personajes (Mekanism, Ars Nouveau, FE, RF) no se traducen.
            8. Usa mayúscula inicial en nombres de objetos y bloques igual que el original, y mantén el estilo breve de la interfaz.
            9. No omitas ninguna entrada. Si un texto no necesita traducción (siglas, números, nombres propios), devuélvelo igual.
            """;

    static String withGlossary(String base, Map<String, String> glossary) {
        if (glossary.isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base).append("\nGlosario obligatorio (original → español):\n");
        glossary.forEach((k, v) -> sb.append("- ").append(k).append(" → ").append(v).append('\n'));
        return sb.toString();
    }
}
