package com.mishiranu.dashchan.chan.yakujimoe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Pair;

import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class YakujiMoeChanMarkup extends ChanMarkup {
	private static final int SUPPORTED_TAGS = TAG_BOLD | TAG_ITALIC | TAG_STRIKE | TAG_SPOILER | TAG_CODE;

	public YakujiMoeChanMarkup() {
		addTag("strong", TAG_BOLD);
		addTag("em", TAG_ITALIC);
		addTag("blockquote", TAG_QUOTE);
		addTag("code", TAG_CODE);
		addTag("del", TAG_STRIKE);
		addTag("span", "spoiler", TAG_SPOILER);
	}

	@Override
	public CommentEditor obtainCommentEditor(String boardName) {
		return new CommentEditor.WakabaMarkCommentEditor();
	}

	@Override
	public boolean isTagSupported(String boardName, int tag) {
		return (SUPPORTED_TAGS & tag) == tag;
	}

	private static final Pattern THREAD_LINK = Pattern.compile("(\\d+).html(?:#(\\d+))?$");

	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString) {
		Matcher matcher = THREAD_LINK.matcher(uriString);
		if (matcher.find()) {
			return new Pair<>(matcher.group(1), matcher.group(2));
		}
		return null;
	}
}
