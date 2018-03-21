import cn.lightfish.offHeap.ParseUtil;
import cn.lightfish.offHeap.StructInfo;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;

import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

public class MyAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        ParseUtil parseUtil = new ParseUtil();
        try {
            Editor editor = e.getData(PlatformDataKeys.EDITOR);
            Project project = e.getData(PlatformDataKeys.PROJECT);

            if (editor == null || project == null) return;
            SelectionModel selectionModel = editor.getSelectionModel();
            String selectedText = selectionModel.getSelectedText();
            if (selectedText == null) return;
            Document document = editor.getDocument();
            String alltext = editor.getDocument().getText();
            boolean isOffset = true;
            int index = alltext.indexOf("/*dystruct offset false*/");
            if (index != -1) {
                isOffset = false;
            } else if (alltext.contains("/*dystruct offset true*/")) {
                isOffset = true;

            } else {
                return;
            }
            Map<String, StructInfo> map = parseUtil.parse(alltext);
            if (map.isEmpty()) return;

            if (!isOffset) {
                runWriteCommandAction(project, () -> {
                    String insertString = map.entrySet()
                            .stream()
                            .flatMap((m) -> m.getValue().getMap().values().stream())
                            .map((entry -> {
                                return String.format("\npublic final static long %s = %dL;\n", entry.name.toUpperCase(), entry.offset);
                            })).filter((s) -> !alltext.contains(s)).distinct()
                            .collect(Collectors.joining(""));
                    document.insertString(index + "/*dystruct offset false*/".length(), insertString);
                });
            }
            int startOffset = selectionModel.getSelectionStart();
            int endOffset = selectionModel.getSelectionEnd();
            String text = selectedText.substring(1, selectedText.length() - 1);
            final boolean isOffset0 = isOffset;
            if (selectedText.startsWith("\"") && selectedText.endsWith("\"")) {
                map.values().stream().flatMap((i) -> {
                    return i.getMap().values().stream();
                }).distinct().filter((k) -> {
                    return k.name.equals(text);
                }).findFirst().ifPresent((member) -> {
                    String newStr;
                    if (isOffset0) {
                        newStr = "/*" + member.name + "*/" + member.offset;
                    } else {
                        newStr = member.name;
                    }
                    runWriteCommandAction(project, () -> {
                        document.replaceString(startOffset, endOffset, newStr);
                    });

                });

            } else {
                String newStr = parseUtil.replaceMemberName(map, selectedText, isOffset);
                if (newStr == null || newStr.isEmpty()) return;
                runWriteCommandAction(project, () -> {
                    document.replaceString(startOffset, endOffset, newStr);
                });
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }
}
