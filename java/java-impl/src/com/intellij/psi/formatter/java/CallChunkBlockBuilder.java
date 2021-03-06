/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.formatter.java;

import com.intellij.formatting.*;
import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.formatter.java.AbstractJavaBlock.newJavaBlock;

public class CallChunkBlockBuilder {

  public static final String FRAGMENT_DEBUG_NAME = "chainFragment";
  public static final String CHAINED_CALL_DEBUG_NAME = "chainedCall";

  private final CommonCodeStyleSettings mySettings;
  private final CommonCodeStyleSettings.IndentOptions myIndentSettings;
  private final JavaCodeStyleSettings myJavaSettings;
  private final FormattingMode myFormattingMode;

  public CallChunkBlockBuilder(@NotNull CommonCodeStyleSettings settings, @NotNull JavaCodeStyleSettings javaSettings,
                               @NotNull FormattingMode formattingMode) {
    mySettings = settings;
    myIndentSettings = settings.getIndentOptions();
    myJavaSettings = javaSettings;
    myFormattingMode = formattingMode;
  }

  @NotNull
  public Block create(@NotNull final List<? extends ASTNode> subNodes,
                      final Wrap wrap,
                      @Nullable final Alignment alignment,
                      int relativeIndentSize) {
    final ArrayList<Block> subBlocks = new ArrayList<>();
    final ASTNode firstNode = subNodes.get(0);
    if (firstNode.getElementType() == JavaTokenType.DOT) {
      AlignmentStrategy strategy = AlignmentStrategy.getNullStrategy();
      Indent indent = relativeIndentSize > 0 ? Indent.getSpaceIndent(relativeIndentSize) : Indent.getNoneIndent();
      Block block = newJavaBlock(firstNode, mySettings, myJavaSettings, indent, null, strategy, myFormattingMode);
      subBlocks.add(block);
      if (subNodes.size() > 1) {
        subBlocks.add(
          createSyntheticBlock(createJavaBlocks(subNodes.subList(1, subNodes.size())), null, null, FRAGMENT_DEBUG_NAME));
      }
      return createSyntheticBlock(subBlocks, alignment, wrap, CHAINED_CALL_DEBUG_NAME);
    }
    else {
      return createSyntheticBlock(createJavaBlocks(subNodes), alignment, null, FRAGMENT_DEBUG_NAME);
    }
  }

  private Block createSyntheticBlock(@NotNull List<Block> subBlocks,
                                     @Nullable Alignment alignment,
                                     @Nullable Wrap wrap,
                                     @NotNull final String debugName) {
    return new SyntheticCodeBlock(subBlocks, alignment, mySettings, myJavaSettings,
                                  Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS), wrap) {
      @Override
      public String getDebugName() {
        return debugName + ": " + SyntheticCodeBlock.class.getSimpleName();
      }
    };
  }

  @NotNull
  private List<Block> createJavaBlocks(@NotNull final List<? extends ASTNode> subNodes) {
    final ArrayList<Block> result = new ArrayList<>();
    for (ASTNode node : subNodes) {
      Indent indent = Indent.getContinuationWithoutFirstIndent(myIndentSettings.USE_RELATIVE_INDENTS);
      result.add(newJavaBlock(node, mySettings, myJavaSettings, indent, null, AlignmentStrategy.getNullStrategy(), myFormattingMode));
    }
    return result;
  }
}