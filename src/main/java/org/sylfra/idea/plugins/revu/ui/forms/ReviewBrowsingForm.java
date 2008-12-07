package org.sylfra.idea.plugins.revu.ui.forms;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sylfra.idea.plugins.revu.RevuBundle;
import org.sylfra.idea.plugins.revu.business.ReviewManager;
import org.sylfra.idea.plugins.revu.model.Review;
import org.sylfra.idea.plugins.revu.model.ReviewItem;
import org.sylfra.idea.plugins.revu.settings.IRevuSettingsListener;
import org.sylfra.idea.plugins.revu.settings.app.RevuAppSettings;
import org.sylfra.idea.plugins.revu.settings.app.RevuAppSettingsComponent;
import org.sylfra.idea.plugins.revu.settings.project.workspace.RevuWorkspaceSettings;
import org.sylfra.idea.plugins.revu.settings.project.workspace.RevuWorkspaceSettingsComponent;
import org.sylfra.idea.plugins.revu.ui.CustomAutoScrollToSourceHandler;
import org.sylfra.idea.plugins.revu.ui.ReviewItemsTable;
import org.sylfra.idea.plugins.revu.ui.forms.reviewitem.ReviewItemTabbedPane;
import org.sylfra.idea.plugins.revu.ui.forms.settings.app.RevuAppSettingsForm;
import org.sylfra.idea.plugins.revu.ui.forms.settings.project.RevuProjectSettingsForm;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:sylfradev@yahoo.fr">Sylvain FRANCOIS</a>
 * @version $Id$
 */
public class ReviewBrowsingForm implements Disposable
{
  private JPanel contentPane;
  private final Project project;
  private final Review review;
  private ReviewItemsTable reviewItemsTable;
  private JComponent toolbar;
  private ReviewItemTabbedPane reviewItemTabbedPane;
  private JSplitPane splitPane;
  private JLabel lbMessage;
  private IRevuSettingsListener<RevuAppSettings> appSettingsListener;
  private MessageClickHandler messageClickHandler;

  public ReviewBrowsingForm(@NotNull Project project, @Nullable Review review)
  {
    this.project = project;
    this.review = review;

    configureUI();

    appSettingsListener = new IRevuSettingsListener<RevuAppSettings>()
    {
      public void settingsChanged(RevuAppSettings settings)
      {
        checkMessage();
      }
    };
    RevuAppSettingsComponent appSettingsComponent =
      ApplicationManager.getApplication().getComponent(RevuAppSettingsComponent.class);
    appSettingsComponent.addListener(appSettingsListener);

    checkMessage();
    checkRowSelected();
  }

  private void createUIComponents()
  {
    final List<ReviewItem> items = retrieveReviewItems();

    reviewItemTabbedPane = new ReviewItemTabbedPane(project);

    reviewItemsTable = new ReviewItemsTable(project, items, review);
    reviewItemsTable.setSelectionModel(new DefaultListSelectionModel()
    {
      @Override
      public void setSelectionInterval(int index0, int index1)
      {
        if (beforeChangeReviewItem())
        {
          super.setSelectionInterval(index0, index1);
          updateUI();
        }
      }
    });
    reviewItemsTable.getListTableModel().addTableModelListener(new TableModelListener()
    {
      public void tableChanged(TableModelEvent e)
      {
        if (e.getType() == TableModelEvent.DELETE)
        {
          reviewItemsTable.getSelectionModel().clearSelection();
          checkMessage();
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              checkRowSelected();
            }
          });
        }
        else if (e.getType() == TableModelEvent.INSERT)
        {
          reviewItemsTable.getSelectionModel().setSelectionInterval(e.getFirstRow(), e.getFirstRow());
        }
      }
    });

    RevuWorkspaceSettingsComponent workspaceSettingsComponent = project.getComponent(
      RevuWorkspaceSettingsComponent.class);

    CustomAutoScrollToSourceHandler autoScrollToSourceHandler
      = new CustomAutoScrollToSourceHandler(workspaceSettingsComponent.getState());
    autoScrollToSourceHandler.install(reviewItemsTable);

    toolbar = createToolbar().getComponent();
  }

  private void checkRowSelected()
  {
    if ((reviewItemsTable.getRowCount() > 0) && (reviewItemsTable.getSelectedRow() == -1))
    {
      reviewItemsTable.getSelectionModel().setSelectionInterval(0, 0);
      updateUI();
    }
  }

  private void configureUI()
  {
    // Later this label might display distinct message depending on app settings
    lbMessage.setIcon(Messages.getInformationIcon());
    lbMessage.setIconTextGap(20);
    messageClickHandler = new MessageClickHandler(project);
    lbMessage.addMouseListener(messageClickHandler);

    RevuWorkspaceSettings workspaceSettings = project.getComponent(RevuWorkspaceSettingsComponent.class).getState();
    splitPane.setOrientation(Integer.parseInt(workspaceSettings.getToolWindowSplitOrientation()));
    splitPane.setDividerLocation(0.5d);
  }

  public JPanel getContentPane()
  {
    return contentPane;
  }

  public JSplitPane getSplitPane()
  {
    return splitPane;
  }

  private boolean beforeChangeReviewItem()
  {
    ReviewItem current = reviewItemsTable.getSelectedObject();

    if (current == null)
    {
      return true;
    }

    // Already called in #updateData, but don't want to save review it item has not changed
    if (!reviewItemTabbedPane.isModified(current))
    {
      return true;
    }

    if (reviewItemTabbedPane.updateData(current))
    {
      project.getComponent(ReviewManager.class).save(current.getReview());
      return true;
    }

    return false;
  }

  public void updateUI()
  {
    checkRowSelected();
    ReviewItem current = reviewItemsTable.getSelectedObject();
    if (current != null)
    {
      reviewItemTabbedPane.updateUI(review, current);
    }
  }

  public void updateReview()
  {
    updateUI();
  }

  public void reviewListChanged()
  {
    reviewItemsTable.getListTableModel().setItems(retrieveReviewItems());
    checkRowSelected();
    checkMessage();
  }

  private void checkMessage()
  {
    String message = null;

    // Login set
    RevuAppSettings appSettings = ApplicationManager.getApplication().getComponent(RevuAppSettingsComponent.class)
      .getState();

    if ((appSettings.getLogin() == null) || (appSettings.getLogin().trim().length() == 0))
    {
      message = RevuBundle.message("general.form.noLogin.text");
      messageClickHandler.setType(MessageClickHandler.Type.NO_LOGIN);
    }
    else
    {
      // No review
      List<Review> reviews = project.getComponent(ReviewManager.class).getReviews(true, false);
      if (reviews.isEmpty())
      {
        message = RevuBundle.message("toolwindow.noReview.text");
        messageClickHandler.setType(MessageClickHandler.Type.NO_REVIEW);
      }
      else
      {
        // No review item
        if (reviewItemsTable.getRowCount() == 0)
        {
          message = RevuBundle.message((review == null)
            ? "toolwindow.noReviewItemForAll.text" : "toolwindow.noReviewItemForThis.text");
          messageClickHandler.setType(MessageClickHandler.Type.NO_REVIEW_ITEM);
        }
      }
    }

    CardLayout cardLayout = (CardLayout) contentPane.getLayout();
    if (message != null)
    {
      lbMessage.setText(message);
      cardLayout.show(contentPane, "label");
    }
    else
    {
      cardLayout.show(contentPane, "form");
    }
  }

  private List<ReviewItem> retrieveReviewItems()
  {
    final List<ReviewItem> items;

    if (review == null)
    {
      items = new ArrayList<ReviewItem>();
      ReviewManager reviewManager = project.getComponent(ReviewManager.class);
      for (Review review : reviewManager.getReviews())
      {
        if (review.isActive())
        {
          items.addAll(review.getItems());
        }
      }
    }
    else
    {
      items = review.getItems();
    }

    return items;
  }

  private ActionToolbar createToolbar()
  {
    String toolbarId = (review == null)
      ? "revu.toolWindow.allReviews"
      : "revu.toolWindow.review";

    ActionGroup actionGroup = (ActionGroup) ActionManager.getInstance().getAction(toolbarId);

    ActionToolbar actionToolbar = ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false);
    actionToolbar.setTargetComponent(reviewItemsTable);
    return actionToolbar;
  }

  public void dispose()
  {
    reviewItemTabbedPane.dispose();
    ApplicationManager.getApplication().getComponent(RevuAppSettingsComponent.class)
      .removeListener(appSettingsListener);
  }

  private static class MessageClickHandler extends MouseAdapter
  {
    enum Type
    {
      NO_LOGIN,
      NO_REVIEW,
      NO_REVIEW_ITEM
    }

    private final Project project;
    private Type type;

    public MessageClickHandler(Project project)
    {
      this.project = project;
    }

    public void setType(Type type)
    {
      this.type = type;
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {
      switch (type)
      {
        case NO_LOGIN:
          ShowSettingsUtil.getInstance().showSettingsDialog(project, RevuAppSettingsForm.class);
          break;

        case NO_REVIEW:
          ShowSettingsUtil.getInstance().showSettingsDialog(project, RevuProjectSettingsForm.class);
          break;

        case NO_REVIEW_ITEM:
          break;
      }
    }
  }
}