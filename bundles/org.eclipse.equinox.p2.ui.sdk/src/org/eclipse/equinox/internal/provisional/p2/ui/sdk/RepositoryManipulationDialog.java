/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.internal.provisional.p2.ui.sdk;

import org.eclipse.equinox.internal.p2.ui.sdk.*;
import org.eclipse.equinox.internal.provisional.p2.ui.IRepositoryManipulator;
import org.eclipse.equinox.internal.provisional.p2.ui.ProvUI;
import org.eclipse.equinox.internal.provisional.p2.ui.dialogs.RepositoryManipulatorDropTarget;
import org.eclipse.equinox.internal.provisional.p2.ui.model.MetadataRepositories;
import org.eclipse.equinox.internal.provisional.p2.ui.viewers.*;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.SameShellProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.PropertyDialogAction;

/**
 * Dialog that allows users to update, add, or remove repositories.
 * 
 * @since 3.4
 */
public class RepositoryManipulationDialog extends TrayDialog {

	private final static int WIDTH_IN_DLUS = 480;
	private final static int HEIGHT_IN_DLUS = 240;
	private static final String BUTTONACTION = "buttonAction"; //$NON-NLS-1$

	StructuredViewerProvisioningListener listener;
	TableViewer repositoryViewer;
	IRepositoryManipulator manipulator;

	/**
	 * Create an instance of this Dialog.
	 * 
	 */
	public RepositoryManipulationDialog(Shell shell, IRepositoryManipulator manipulator) {
		super(shell);
		this.manipulator = manipulator;
	}

	protected void configureShell(Shell shell) {
		shell.setText(ProvSDKMessages.RepositoryManipulationDialog_UpdateSitesDialogTitle);
		super.configureShell(shell);
	}

	protected Control createDialogArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginWidth = convertHorizontalDLUsToPixels(2);
		layout.marginHeight = convertVerticalDLUsToPixels(2);

		composite.setLayout(layout);

		// Table of available repositories
		repositoryViewer = new TableViewer(composite, SWT.MULTI | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		setTableColumns(repositoryViewer.getTable());
		repositoryViewer.setContentProvider(new RepositoryContentProvider(ProvSDKUIActivator.getDefault().getQueryProvider()));
		repositoryViewer.setLabelProvider(new ProvElementLabelProvider());

		// Input last
		repositoryViewer.setInput(getInput());

		DropTarget target = new DropTarget(repositoryViewer.getControl(), DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
		target.setTransfer(new Transfer[] {URLTransfer.getInstance(), FileTransfer.getInstance()});
		target.addDropListener(new RepositoryManipulatorDropTarget(manipulator, repositoryViewer.getControl()));

		GridData data = new GridData(GridData.FILL_BOTH);
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		data.widthHint = convertHorizontalDLUsToPixels(WIDTH_IN_DLUS);
		data.heightHint = convertVerticalDLUsToPixels(HEIGHT_IN_DLUS);
		repositoryViewer.getControl().setLayoutData(data);

		// Vertical buttons
		Composite verticalButtonBar = (Composite) createVerticalButtonBar(composite);
		data = new GridData(GridData.FILL_VERTICAL);
		verticalButtonBar.setLayoutData(data);
		listener = new StructuredViewerProvisioningListener(repositoryViewer, StructuredViewerProvisioningListener.PROV_EVENT_METADATA_REPOSITORY, ProvSDKUIActivator.getDefault().getQueryProvider());
		ProvUI.addProvisioningListener(listener);
		composite.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent event) {
				ProvUI.removeProvisioningListener(listener);
			}
		});
		Dialog.applyDialogFont(composite);
		return composite;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
	 */
	protected boolean isResizable() {
		return true;
	}

	private Button createVerticalButton(Composite parent, String label, boolean defaultButton) {
		Button button = new Button(parent, SWT.PUSH);
		button.setText(label);

		GridData data = setVerticalButtonLayoutData(button);
		data.horizontalAlignment = GridData.FILL;

		button.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				verticalButtonPressed(event);
			}
		});
		button.setToolTipText(label);
		if (defaultButton) {
			Shell shell = parent.getShell();
			if (shell != null) {
				shell.setDefaultButton(button);
			}
		}
		return button;
	}

	void verticalButtonPressed(Event event) {
		Object data = event.widget.getData(BUTTONACTION);
		if (data == null || !(data instanceof IAction)) {
			return;
		}
		IAction action = (IAction) data;
		action.runWithEvent(event);
	}

	private GridData setVerticalButtonLayoutData(Button button) {
		GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		int widthHint = convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
		Point minSize = button.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		data.widthHint = Math.max(widthHint, minSize.x);
		button.setLayoutData(data);
		return data;
	}

	private void setTableColumns(Table table) {
		table.setHeaderVisible(true);
		String[] columnHeaders = {ProvSDKMessages.RepositoryManipulationDialog_NameColumnHeader, ProvSDKMessages.RepositoryManipulationDialog_LocationColumnHeader};
		for (int i = 0; i < columnHeaders.length; i++) {
			TableColumn tc = new TableColumn(table, SWT.NONE, i);
			tc.setResizable(true);
			tc.setText(columnHeaders[i]);
			tc.setWidth(convertHorizontalDLUsToPixels(IDialogConstants.ENTRY_FIELD_WIDTH));
		}
	}

	private Control createVerticalButtonBar(Composite parent) {
		// Create composite.
		Composite composite = new Composite(parent, SWT.NULL);

		// create a layout with spacing and margins appropriate for the font
		// size.
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginWidth = 5;
		layout.marginHeight = 0;
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		composite.setLayout(layout);

		// Add the buttons to the button bar.
		Button button = createVerticalButton(composite, ProvSDKMessages.RepositoryManipulationDialog_PropertiesButton, false);
		button.setData(BUTTONACTION, new PropertyDialogAction(new SameShellProvider(parent.getShell()), repositoryViewer));
		button = createVerticalButton(composite, ProvSDKMessages.RepositoryManipulationDialog_AddButton, false);
		button.setData(BUTTONACTION, new AddColocatedRepositoryAction(repositoryViewer, getShell()));
		button = createVerticalButton(composite, ProvSDKMessages.RepositoryManipulationDialog_RemoveButton, false);
		button.setData(BUTTONACTION, new RemoveColocatedRepositoryAction(repositoryViewer, getShell()));
		return composite;
	}

	private Object getInput() {
		MetadataRepositories input = new MetadataRepositories();
		input.setQueryProvider(ProvSDKUIActivator.getDefault().getQueryProvider());
		input.setQueryContext(new AvailableIUViewQueryContext(AvailableIUViewQueryContext.VIEW_BY_REPO));
		return input;
	}
}
