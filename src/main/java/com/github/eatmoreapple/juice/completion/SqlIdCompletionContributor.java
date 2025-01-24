package com.github.eatmoreapple.juice.completion;

import com.goide.psi.GoTypeSpec;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author pjh
 * @date 2025/1/24
 */
public class SqlIdCompletionContributor extends CompletionContributor {
    private static final Logger log = LoggerFactory.getLogger(SqlIdCompletionContributor.class);

    public SqlIdCompletionContributor() {
        // 为 id 属性添加自动完成
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().inside(
                        XmlPatterns.xmlAttribute().withName("id").withParent(
                                XmlPatterns.xmlTag().withName(
                                        PlatformPatterns.string().oneOf("select", "insert", "update", "delete")
                                )
                        )
                ),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        try {
                            PsiElement position = parameters.getPosition();

                            // 向上查找到 XmlAttribute
                            PsiElement current = position;
                            while (current != null && !(current instanceof XmlAttribute)) {
                                current = current.getParent();
                            }

                            if (!(current instanceof XmlAttribute)) {
                                return;
                            }

                            XmlAttribute attribute = (XmlAttribute) current;
                            XmlTag tag = attribute.getParent();

                            if (tag == null) {
                                return;
                            }

                            // 获取 mapper 标签
                            XmlTag mapperTag = tag.getParentTag();
                            if (mapperTag == null || !mapperTag.getName().equals("mapper")) {
                                return;
                            }

                            // 获取 namespace
                            String namespace = mapperTag.getAttributeValue("namespace");
                            if (namespace == null || namespace.isEmpty()) {
                                return;
                            }
                            String[] split = namespace.split("\\.");
                            GlobalSearchScope globalSearchScope = GlobalSearchScope.allScope(position.getProject());
                            Collection<GoTypeSpec> elements = StubIndex.getElements(GoTypesIndex.KEY, split[split.length - 1], position.getProject(), globalSearchScope, GoTypeSpec.class);
                            Set<String> resultMethods = elements.stream().flatMap(element -> element.getAllMethods().stream().map(method -> method.getIdentifier().getText()))
                                    .collect(Collectors.toSet());
                            resultMethods.forEach(resultMethod -> result.addElement(
                                    LookupElementBuilder.create(resultMethod)
                                            .withLookupString(resultMethod.toLowerCase())
                            ));
                        } catch (Exception e) {
                            log.warn("Error in completion contributor", e);
                        }
                    }
                }
        );
    }
}
