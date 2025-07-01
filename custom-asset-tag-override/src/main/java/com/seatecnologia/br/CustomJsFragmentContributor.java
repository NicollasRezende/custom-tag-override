package com.seatecnologia.br;

import com.liferay.fragment.contributor.FragmentCollectionContributor;
import com.liferay.portal.kernel.servlet.taglib.DynamicInclude;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Component;

/**
 * Injeta JavaScript para permitir + nas tags
 */
@Component(
        immediate = true,
        service = DynamicInclude.class
)
public class CustomJsFragmentContributor implements DynamicInclude {

    @Override
    public void include(HttpServletRequest request, HttpServletResponse response, String key)
            throws IOException {

        PrintWriter printWriter = response.getWriter();

        printWriter.println("<script>");
        printWriter.println("(function() {");
        printWriter.println("    // Override da validação de tags no frontend");
        printWriter.println("    if (window.Liferay && window.Liferay.AssetTagsSelector) {");
        printWriter.println("        var originalValidate = window.Liferay.AssetTagsSelector.prototype._validateTag;");
        printWriter.println("        if (originalValidate) {");
        printWriter.println("            window.Liferay.AssetTagsSelector.prototype._validateTag = function(tag) {");
        printWriter.println("                // Permite qualquer tag, incluindo com +");
        printWriter.println("                return true;");
        printWriter.println("            };");
        printWriter.println("        }");
        printWriter.println("    }");
        printWriter.println("");
        printWriter.println("    // Override para Clay MultiSelect se existir");
        printWriter.println("    document.addEventListener('DOMContentLoaded', function() {");
        printWriter.println("        var interval = setInterval(function() {");
        printWriter.println("            var multiSelects = document.querySelectorAll('[id*=\"assetTagNames\"]');");
        printWriter.println("            if (multiSelects.length > 0) {");
        printWriter.println("                clearInterval(interval);");
        printWriter.println("                console.log('Removendo validação de + nas tags...');");
        printWriter.println("                // Remove event listeners que possam validar");
        printWriter.println("                multiSelects.forEach(function(input) {");
        printWriter.println("                    var newInput = input.cloneNode(true);");
        printWriter.println("                    input.parentNode.replaceChild(newInput, input);");
        printWriter.println("                });");
        printWriter.println("            }");
        printWriter.println("        }, 500);");
        printWriter.println("    });");
        printWriter.println("})();");
        printWriter.println("</script>");
    }

    @Override
    public void register(DynamicIncludeRegistry dynamicIncludeRegistry) {
        dynamicIncludeRegistry.register(
                "com.liferay.portal.kernel.servlet.taglib.html.bottom");
    }
}