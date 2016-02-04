package com.mishiranu.dashchan.chan.dobrochan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.text.GroupParser;
import chan.text.ParseException;
import chan.util.StringUtils;

public class DobrochanBoardsParser implements GroupParser.Callback
{
	private static final String[] PREFERRED_BOARDS_ORDER = {"Общее", "Доброчан", "Аниме", "На пробу"};
	
	private final String mSource;
	
	private final ArrayList<BoardCategory> mBoardCategories = new ArrayList<>();
	private final ArrayList<Board> mBoards = new ArrayList<>();
	
	private String mBoardCategoryTitle;
	private String mBoardName;

	private static final int EXPECT_NONE = 0;
	private static final int EXPECT_CATEGORY = 1;
	private static final int EXPECT_BOARD = 2;
	
	private int mExpect = EXPECT_NONE;
	
	private static final Pattern BOARD_NAME_PATTERN = Pattern.compile("/(\\w+)/index.xhtml");
	
	public DobrochanBoardsParser(String source)
	{
		mSource = source;
	}
	
	public ArrayList<BoardCategory> convert() throws ParseException
	{
		GroupParser.parse(mSource, this);
		closeCategory();
		ArrayList<BoardCategory> boardCategories = new ArrayList<>();
		for (String title : PREFERRED_BOARDS_ORDER)
		{
			for (BoardCategory boardCategory : mBoardCategories)
			{
				if (title.equals(boardCategory.getTitle()))
				{
					Arrays.sort(boardCategory.getBoards());
					boardCategories.add(boardCategory);
					break;
				}
			}
		}
		return boardCategories;
	}
	
	private void closeCategory()
	{
		if (mBoardCategoryTitle != null)
		{
			if (mBoards.size() > 0) mBoardCategories.add(new BoardCategory(mBoardCategoryTitle, mBoards));
			mBoardCategoryTitle = null;
			mBoards.clear();
		}
	}
	
	@Override
	public boolean onStartElement(GroupParser parser, String tagName, String attrs)
	{
		if ("td".equals(tagName))
		{
			String cssClass = parser.getAttr(attrs, "class");
			if ("header".equals(cssClass))
			{
				closeCategory();
				mExpect = EXPECT_CATEGORY;
				return true;
			}
		}
		else if (mBoardCategoryTitle != null)
		{
			if ("a".equals(tagName))
			{
				String href = parser.getAttr(attrs, "href");
				Matcher matcher = BOARD_NAME_PATTERN.matcher(href);
				if (matcher.matches())
				{
					mBoardName = matcher.group(1);
					mExpect = EXPECT_BOARD;
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public void onEndElement(GroupParser parser, String tagName)
	{
		
	}
	
	@Override
	public void onText(GroupParser parser, String source, int start, int end)
	{
		
	}
	
	@Override
	public void onGroupComplete(GroupParser parser, String text)
	{
		switch (mExpect)
		{
			case EXPECT_CATEGORY:
			{
				mBoardCategoryTitle = StringUtils.clearHtml(text);
				break;
			}
			case EXPECT_BOARD:
			{
				text = StringUtils.clearHtml(text).trim();
				text = text.substring(text.indexOf('—') + 2);
				mBoards.add(new Board(mBoardName, text));
				break;
			}
		}
		mExpect = EXPECT_NONE;
	}
}