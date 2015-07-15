/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.classMembers.AbstractMemberInfoModel
import com.intellij.refactoring.memberPullUp.PullUpDialogBase
import com.intellij.refactoring.memberPullUp.PullUpProcessor
import com.intellij.refactoring.util.DocCommentPolicy
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.refactoring.memberInfo.*
import org.jetbrains.kotlin.psi.*
import java.awt.event.ItemEvent
import javax.swing.JComboBox

public class KotlinPullUpDialog(
        project: Project,
        private val classOrObject: JetClassOrObject,
        superClasses: List<JetClass>,
        memberInfoStorage: KotlinMemberInfoStorage
) : PullUpDialogBase<KotlinMemberInfoStorage, KotlinMemberInfo, JetNamedDeclaration, JetClassOrObject>(
        project, classOrObject, superClasses, memberInfoStorage, PULL_MEMBERS_UP
) {
    init {
        init()
    }

    private inner class MemberInfoModelImpl : AbstractMemberInfoModel<JetNamedDeclaration, KotlinMemberInfo>() {
        // Abstract members remain abstract
        override fun isFixedAbstract(memberInfo: KotlinMemberInfo?) = true

        /*
         * Any non-abstract function can change abstractness.
         *
         * Non-abstract property with initializer or delegate is always made abstract.
         * Any other non-abstract property can change abstractness.
         *
         * Classes do not have abstractness
         */
        override fun isAbstractEnabled(memberInfo: KotlinMemberInfo): Boolean {
            val superClass = getSuperClass() ?: return false
            if (!superClass.isInterface()) return true

            val member = memberInfo.getMember()
            return member is JetNamedFunction || (member is JetProperty && member.getInitializer() == null && member.getDelegate() == null)
        }

        override fun isAbstractWhenDisabled(memberInfo: KotlinMemberInfo): Boolean {
            return memberInfo.getMember() is JetProperty
        }

        override fun isMemberEnabled(memberInfo: KotlinMemberInfo): Boolean {
            val superClass = getSuperClass() ?: return true
            if (memberInfo in memberInfoStorage.getDuplicatedMemberInfos(superClass)) return false
            return true
        }
    }

    protected val memberInfoStorage: KotlinMemberInfoStorage get() = myMemberInfoStorage

    override fun getDimensionServiceKey() = "#" + javaClass.getName()

    override fun getSuperClass() = super.getSuperClass() as? JetClass

    override fun initClassCombo(classCombo: JComboBox) {
        classCombo.setRenderer(JetClassOrObjectCellRenderer())
        classCombo.addItemListener { event ->
            if (event.getStateChange() == ItemEvent.SELECTED) {
                myMemberSelectionPanel?.getTable()?.let {
                    it.setMemberInfos(myMemberInfos)
                    it.fireExternalDataChange()
                }
            }
        }
    }

    override fun createMemberInfoModel() = MemberInfoModelImpl()

    override fun getPreselection() = mySuperClasses.firstOrNull()

    override fun createMemberSelectionTable(infos: MutableList<KotlinMemberInfo>) =
            KotlinMemberSelectionTable(infos, null, "Make abstract")

    override fun doAction() {
        invokeRefactoring(createProcessor(myClass, getSuperClass()!!, getSelectedMemberInfos()))
    }

    companion object {
        fun createProcessor(sourceClass: JetClassOrObject,
                            targetClass: JetClass,
                            memberInfos: List<KotlinMemberInfo>): PullUpProcessor {
            return PullUpProcessor(sourceClass.toLightClass(),
                                   targetClass.toLightClass(),
                                   memberInfos.map { it.toJavaMemberInfo() }.filterNotNull().toTypedArray(),
                                   DocCommentPolicy<PsiComment>(JavaRefactoringSettings.getInstance().PULL_UP_MEMBERS_JAVADOC))
        }
    }
}