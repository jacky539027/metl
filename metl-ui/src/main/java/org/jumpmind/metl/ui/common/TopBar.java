/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.metl.ui.common;

import static org.apache.commons.lang.StringUtils.isBlank;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vaadin.navigator.ViewChangeListener;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Page;
import com.vaadin.server.VaadinSession;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.MenuBar;
import com.vaadin.ui.MenuBar.Command;
import com.vaadin.ui.MenuBar.MenuItem;

public class TopBar extends HorizontalLayout implements ViewChangeListener {

    private static final long serialVersionUID = 1L;

    MenuBar menuBar;

    ViewManager viewManager;

    Map<String, MenuItem> viewToButtonMapping;

    List<MenuItem> categoryItems = new ArrayList<MenuBar.MenuItem>();

    @SuppressWarnings("serial")
    public TopBar(ViewManager vm, ApplicationContext context) {
        setWidth(100, Unit.PERCENTAGE);
        this.viewManager = vm;
        this.viewManager.addViewChangeListener(this);

        viewToButtonMapping = new HashMap<String, MenuItem>();

        menuBar = new MenuBar();
        menuBar.setWidth(100, Unit.PERCENTAGE);
        addComponent(menuBar);
        setExpandRatio(menuBar, 1.0f);

        Button helpButton = new Button("Help", FontAwesome.QUESTION_CIRCLE);
        helpButton.addClickListener(event -> openHelp(event));
        addComponent(helpButton);

        Button settingsButton = new Button(context.getUser().getLoginId(), FontAwesome.GEAR);
        settingsButton.addClickListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
            }
        });
        addComponent(settingsButton);

        Button logoutButton = new Button("Logout", FontAwesome.SIGN_OUT);
        logoutButton.addClickListener(event -> logout());
        addComponent(logoutButton);

        Map<Category, List<TopBarLink>> menuItemsByCategory = viewManager.getMenuItemsByCategory();
        Set<Category> categories = menuItemsByCategory.keySet();
        for (Category category : categories) {
            if (!context.getUser().hasPrivilege(category.name())) {
                continue;
            }
            List<TopBarLink> links = menuItemsByCategory.get(category);
            if (viewManager.getDefaultView() == null && links.size() > 0) {
                viewManager.setDefaultView(links.get(0).id());
            }
            MenuItem categoryItem = null;
            if (links.size() > 1) {
                categoryItem = menuBar.addItem(category.name(), null);
                categoryItem.setCheckable(true);
                categoryItems.add(categoryItem);
            }
            for (final TopBarLink menuLink : links) {
                Command command = new Command() {

                    private static final long serialVersionUID = 1L;

                    @Override
                    public void menuSelected(MenuItem selectedItem) {
                        uncheckAll();
                        selectedItem.setChecked(true);
                        viewManager.navigateTo(menuLink.id());
                    }
                };
                MenuItem menuItem = null;
                if (categoryItem == null) {
                    menuItem = menuBar.addItem(menuLink.name(), command);
                } else {
                    menuItem = categoryItem.addItem(menuLink.name(), command);
                }
                menuItem.setCheckable(true);
                viewToButtonMapping.put(menuLink.id(), menuItem);
            }
        }
        viewManager.navigateTo(viewManager.getDefaultView());
    }
    
    protected void logout() {
        URI uri = Page.getCurrent().getLocation();
        VaadinSession.getCurrent().close();
        Page.getCurrent().setLocation(uri.getPath());
    }

    protected void openHelp(ClickEvent event) {
        String docUrl = Page.getCurrent().getLocation().toString();
        docUrl = docUrl.substring(0, docUrl.indexOf("/app"));
        Page.getCurrent().open(docUrl + "/doc/html/user-guide.html", "doc");
    }

    protected void uncheckAll() {
        for (MenuItem menuItem : categoryItems) {
            menuItem.setChecked(false);
        }
        for (MenuItem menuItem : viewToButtonMapping.values()) {
            menuItem.setChecked(false);
        }
    }

    @Override
    public boolean beforeViewChange(final ViewChangeEvent event) {
        return true;
    }

    @Override
    public void afterViewChange(final ViewChangeEvent event) {
        String view = event.getViewName();
        if (isBlank(view)) {
            view = viewManager.getDefaultView();
        }
        MenuItem menuItem = viewToButtonMapping.get(view);
        if (menuItem != null) {
            uncheckAll();
            menuItem.setChecked(true);
        }
    }

}