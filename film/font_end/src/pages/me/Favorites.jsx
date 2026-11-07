import { useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { meApi } from '../../api/endpoints';
import { MovieGrid } from '../../components/MovieCard';
import { Pagination, QueryState } from '../../components/ui';

export default function Favorites() {
  const [page, setPage] = useState(0);
  const list = useQuery({ queryKey: ['favorites', page], queryFn: () => meApi.favorites(page), placeholderData: keepPreviousData });
  return (
    <>
      <title>Phim yêu thích · ReviewFilm</title>
      <h1>Phim yêu thích</h1>
      <QueryState query={list} isEmpty={(d) => d.content.length === 0} empty={<>Chưa có phim yêu thích. <Link to="/phim" className="link-btn">Tìm phim</Link></>}>
        {(data) => (
          <>
            <MovieGrid movies={data.content} />
            <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )}
      </QueryState>
    </>
  );
}
