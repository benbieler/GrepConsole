package krasa.grepconsole.grep.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.regex.PatternSyntaxException;

import javax.swing.*;

import krasa.grepconsole.grep.CopyListenerModel;
import krasa.grepconsole.grep.OpenGrepConsoleAction;
import krasa.grepconsole.grep.listener.GrepCopyingFilterListener;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.TextFieldWithStoredHistory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBDimension;

public class GrepPanel extends JPanel implements Disposable {

	public static final NotificationGroup GROUP_DISPLAY_ID_ERROR = new NotificationGroup("Grep Console error",
			NotificationDisplayType.BALLOON, false);

	@Nullable
	private ConsoleViewImpl originalConsole;
	private final ConsoleViewImpl newConsole;
	private final GrepCopyingFilterListener copyingListener;
	private final RunnerLayoutUi runnerLayoutUi;
	private MyTextFieldWithStoredHistory expressionTextField;
	private TextFieldWithStoredHistory unlessExpressionTextField;
	private JBCheckBox matchCase;
	private JButton applyButton;
	private JButton reloadButton;
	private JButton sourceButton;
	private JPanel rootComponent;
	private JBCheckBox wholeLine;
	private JBCheckBox regex;
	private JLabel expLabel;
	private JLabel unlessLabel;
	private JButton clearHistory;
	private OpenGrepConsoleAction.Callback applyCallback;

	public JPanel getRootComponent() {
		return rootComponent;
	}

	private void createUIComponents() {
		expressionTextField = new MyTextFieldWithStoredHistory("ConsoleQuickFilterPanel-expression");
		expressionTextField.setHistorySize(50);
		// expression.setBorder(JBUI.Borders.empty());
		expressionTextField.setMinimumAndPreferredWidth(300);
		unlessExpressionTextField = new TextFieldWithStoredHistory("ConsoleQuickFilterPanel-unlessExpression");
		unlessExpressionTextField.setMinimumAndPreferredWidth(150);
		unlessExpressionTextField.setHistorySize(50);
		// this.unlessExpression.setBorder(JBUI.Borders.empty());
	}

	public GrepPanel(final ConsoleViewImpl originalConsole, final ConsoleViewImpl newConsole,
					 GrepCopyingFilterListener copyingListener, final String pattern, final RunnerLayoutUi runnerLayoutUi) {
		this.originalConsole = originalConsole;
		this.newConsole = newConsole;
		this.copyingListener = copyingListener;
		this.runnerLayoutUi = runnerLayoutUi;
		java.util.List<GrepOptionsItem> history = expressionTextField.reset();
		if (!StringUtils.isEmpty(pattern)) {
			GrepOptionsItem selectedItem = null;
			for (GrepOptionsItem grepOptionsItem : history) {
				if (grepOptionsItem.getExpression().equals(pattern)) {
					selectedItem = grepOptionsItem;
					break;
				}
			}
			if (selectedItem == null) {
				selectedItem = new GrepOptionsItem().setExpression(pattern);
			}
			this.expressionTextField.setSelectedItem(selectedItem);
		}
		updateGrepOptions((GrepOptionsItem) expressionTextField.getSelectedItem());

		expLabel.setLabelFor(expressionTextField);
		unlessLabel.setLabelFor(unlessExpressionTextField);
		actions();
		buttons();
		expressionTextField.addItemListener(new ItemChangeListener());
		CopyListenerModel copyListenerModel = new CopyListenerModel(matchCase.isSelected(), wholeLine.isSelected(),
				regex.isSelected(), expressionTextField.getText(), unlessExpressionTextField.getText());
		this.expressionTextField.addCurrentTextToHistory(copyListenerModel);
	}

	class ItemChangeListener implements ItemListener {
		@Override
		public void itemStateChanged(ItemEvent event) {
			if (event.getStateChange() == ItemEvent.SELECTED) {
				GrepOptionsItem item = (GrepOptionsItem) event.getItem();
				updateGrepOptions(item);
			}
		}
	}
	protected void updateGrepOptions(GrepOptionsItem selectedItem) {
		if (selectedItem != null) {
			wholeLine.setSelected(selectedItem.isWholeLine());
			regex.setSelected(selectedItem.isRegex());
			matchCase.setSelected(selectedItem.isCaseSensitive());
		}
	}

	protected void actions() {
		KeyAdapter reload = new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				final int keyCode = e.getKeyCode();
				if (keyCode == KeyEvent.VK_ENTER && e.isAltDown()) {
					reloadButton.doClick();
				} else if (keyCode == KeyEvent.VK_ENTER) {
					applyButton.doClick();
				}
			}
		};
		expressionTextField.addKeyboardListener(reload);
		unlessExpressionTextField.addKeyboardListener(reload);
	}

	protected void buttons() {
		applyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				apply();
			}
		});
		reloadButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				reload();
			}
		});
		clearHistory.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				expressionTextField.clearHistory();

				PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
				propertiesComponent.unsetValue("ConsoleQuickFilterPanel-unlessExpression");
				unlessExpressionTextField.reset();
			}
		});
		sourceButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Content[] contents = runnerLayoutUi.getContents();
				for (Content content : contents) {
					JComponent component = content.getComponent();
					if (component == originalConsole) {
						runnerLayoutUi.selectAndFocus(content, true, true);
					} else if (isChild(component, originalConsole)) {
						runnerLayoutUi.selectAndFocus(content, true, true);
					}
				}

			}

			private boolean isChild(JComponent component, ConsoleViewImpl originalConsole) {
				return component.getComponentZOrder(originalConsole) != -1;
			}
		});
		buttonSize(sourceButton);
		buttonSize(reloadButton);
		buttonSize(applyButton);
		buttonSize(clearHistory);
	}

	protected void reload() {
		apply();
		newConsole.clear();
		if (originalConsole != null) {
			Editor editor = originalConsole.getEditor();
			Document document = editor.getDocument();
			String text = document.getText();
			for (String s : text.split("\n")) {
				copyingListener.process(s + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
			}
		}
	}

	public void apply() {
		if (applyCallback != null) {
			CopyListenerModel copyListenerModel = new CopyListenerModel(matchCase.isSelected(),
					wholeLine.isSelected(), regex.isSelected(), expressionTextField.getText(),
					unlessExpressionTextField.getText());

			try {
				applyCallback.apply(copyListenerModel);
				expressionTextField.addCurrentTextToHistory(copyListenerModel);
				unlessExpressionTextField.addCurrentTextToHistory();
			} catch (PatternSyntaxException e) {
				final Notification notification = GROUP_DISPLAY_ID_ERROR.createNotification(
						"Grep: invalid regexp", NotificationType.WARNING);
				ApplicationManager.getApplication().invokeLater(new Runnable() {
					@Override
					public void run() {
						Notifications.Bus.notify(notification, newConsole.getProject());
					}
				});
			}
		}

	}

	private void buttonSize(JButton sourceButton) {
		Dimension oldSize = sourceButton.getPreferredSize();
		JBDimension newSize = new JBDimension(oldSize.width, oldSize.height - 5);
		sourceButton.setPreferredSize(newSize);
	}

	@Override
	public void dispose() {
		originalConsole = null;
		applyButton.setEnabled(false);
		reloadButton.setEnabled(false);
		sourceButton.setEnabled(false);
	}

	public void setApplyCallback(OpenGrepConsoleAction.Callback applyCallback) {
		this.applyCallback = applyCallback;
		apply();
	}
}
