package rses.visualisation;

import java.awt.event.MouseEvent;
import java.io.BufferedWriter;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import rses.util.FileUtil;





public class ModelDisplayPanel extends JScrollPane implements java.awt.event.ActionListener
{	
	private rses.Model model = null;

	/** Update the panel so that it is displaying a new model.
	 *   Does <i>not</i> repaint the panel.
	 * 
	 * @param newModel
	 */
	public ModelDisplayPanel(rses.Model newModel, String[] paramNames)
	{
		this.model = newModel;
		JPanel internalPanel = new JPanel();
		internalPanel.setLayout(new BoxLayout(internalPanel, BoxLayout.Y_AXIS));
		int nd = newModel.getNumParameters();
		for(int i =0; i < nd; i++) 
			internalPanel.add(new JLabel(paramNames[i]+" = "+newModel.getModelParameter(i)));
			
		String mfstring = "misfit = unknown";
		if(newModel.isMisfitAvailable())
			mfstring = "misfit = "+newModel.getMisfit();
			
		internalPanel.add(new JLabel(mfstring));
			
		
		this.setViewportView(internalPanel);
		this.revalidate();
		
		//now add a popup menu listener so that we can save the model to a file
		javax.swing.JPopupMenu popmenu = new javax.swing.JPopupMenu();
		popmenu.add("Save model to file").addActionListener(this);
		
		class MouseListImpl extends java.awt.event.MouseAdapter {
			private javax.swing.JPopupMenu popmenu;
			MouseListImpl(javax.swing.JPopupMenu popmenu) { this.popmenu = popmenu; }
			public void mouseReleased(MouseEvent e) { maybeShowPopup(e); } 
			public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
			private void maybeShowPopup(MouseEvent e) {
				if (e.isPopupTrigger())
					popmenu.show(e.getComponent(),e.getX(), e.getY());
			}
		}
		java.awt.event.MouseListener mouselist = new MouseListImpl(popmenu); 
		this.addMouseListener(mouselist);
	}
	
	
	
	
	public void actionPerformed(java.awt.event.ActionEvent event) 
	{
		if(event.getActionCommand().equals("Save model to file"))
		{
			java.io.File f = FileUtil.guiSaveFile(this, "Save model to file");
			String reportstring = "Unknown error saving file...";
			if(f == null) return;
			try {
				java.io.BufferedWriter writer = new BufferedWriter(new java.io.FileWriter(f));
				writer.write(model.toString());
				writer.close();
				reportstring = "File sucessfully saved";
			}
			catch(java.io.IOException ioe) {
				reportstring = "IO Error saving to file.";
			}
			finally {
				javax.swing.JOptionPane.showMessageDialog(this, reportstring);
			}
		}
		else throw new IllegalStateException();
	}

}





