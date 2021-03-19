package io.spring.graphql.users;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.InputArgument;
import graphql.execution.DataFetcherResult;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultPageInfo;
import io.spring.Util;
import io.spring.application.CommentQueryService;
import io.spring.application.CursorPageParameter;
import io.spring.application.CursorPager;
import io.spring.application.CursorPager.Direction;
import io.spring.application.data.ArticleData;
import io.spring.application.data.CommentData;
import io.spring.core.user.User;
import io.spring.graphql.DgsConstants.ARTICLE;
import io.spring.graphql.DgsConstants.COMMENTPAYLOAD;
import io.spring.graphql.types.Comment;
import io.spring.graphql.types.CommentEdge;
import io.spring.graphql.types.CommentsConnection;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;

@DgsComponent
public class CommentDatafetcher {
  private CommentQueryService commentQueryService;

  @Autowired
  public CommentDatafetcher(CommentQueryService commentQueryService) {
    this.commentQueryService = commentQueryService;
  }

  @DgsData(parentType = COMMENTPAYLOAD.TYPE_NAME, field = COMMENTPAYLOAD.Comment)
  public DataFetcherResult<Comment> getComment(DgsDataFetchingEnvironment dfe) {
    CommentData comment = dfe.getLocalContext();
    Comment commentResult = buildCommentResult(comment);
    return DataFetcherResult.<Comment>newResult()
        .data(commentResult)
        .localContext(
            new HashMap<String, Object>() {
              {
                put(comment.getId(), comment);
              }
            })
        .build();
  }

  @DgsData(parentType = ARTICLE.TYPE_NAME, field = ARTICLE.Comments)
  public DataFetcherResult<CommentsConnection> articleComments(
      @InputArgument("first") Integer first,
      @InputArgument("after") String after,
      @InputArgument("last") Integer last,
      @InputArgument("before") String before,
      DgsDataFetchingEnvironment dfe) {

    if (first == null && last == null) {
      throw new IllegalArgumentException("first 和 last 必须只存在一个");
    }

    User current = SecurityUtil.getCurrentUser().orElse(null);
    ArticleData articleData = dfe.getLocalContext();

    CursorPager<CommentData> comments;
    if (first != null) {
      comments =
          commentQueryService.findByArticleIdWithCursor(
              articleData.getId(), current, new CursorPageParameter(after, first, Direction.NEXT));
    } else {
      comments =
          commentQueryService.findByArticleIdWithCursor(
              articleData.getId(), current, new CursorPageParameter(before, last, Direction.PREV));
    }
    graphql.relay.PageInfo pageInfo = buildCommentPageInfo(comments);
    CommentsConnection result =
        CommentsConnection.newBuilder()
            .pageInfo(pageInfo)
            .edges(
                comments.getData().stream()
                    .map(
                        a ->
                            CommentEdge.newBuilder()
                                .cursor(a.getCursor())
                                .node(buildCommentResult(a))
                                .build())
                    .collect(Collectors.toList()))
            .build();
    return DataFetcherResult.<CommentsConnection>newResult()
        .data(result)
        .localContext(
            comments.getData().stream().collect(Collectors.toMap(CommentData::getId, c -> c)))
        .build();
  }

  private DefaultPageInfo buildCommentPageInfo(CursorPager<CommentData> comments) {
    return new DefaultPageInfo(
        Util.isEmpty(comments.getStartCursor())
            ? null
            : new DefaultConnectionCursor(comments.getStartCursor()),
        Util.isEmpty(comments.getEndCursor())
            ? null
            : new DefaultConnectionCursor(comments.getEndCursor()),
        comments.hasPrevious(),
        comments.hasNext());
  }

  private Comment buildCommentResult(CommentData comment) {
    return Comment.newBuilder()
        .id(comment.getId())
        .body(comment.getBody())
        .updatedAt(ISODateTimeFormat.dateTime().withZoneUTC().print(comment.getCreatedAt()))
        .createdAt(ISODateTimeFormat.dateTime().withZoneUTC().print(comment.getCreatedAt()))
        .build();
  }
}